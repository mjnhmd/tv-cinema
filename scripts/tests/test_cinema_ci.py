import base64
import contextlib
import importlib.util
import io
import stat
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "cinema_ci", ROOT / "scripts" / "cinema-ci.py"
)
CI = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CI)


class VersionTest(unittest.TestCase):
    def test_timestamp_versions_match_the_pinned_recipe(self):
        code, name = CI.release_version("2026-10-07T12:00:00Z")
        self.assertEqual(214_537_600, code)
        self.assertEqual("0.1.4+20261007.120000", name)

    def test_timezone_offsets_represent_the_same_version(self):
        self.assertEqual(
            CI.release_version("2026-10-07T12:00:00Z"),
            CI.release_version("2026-10-07T20:00:00+08:00"),
        )

    def test_manual_version_requires_both_fields_and_a_monotonic_code(self):
        self.assertEqual((10_001, "0.1.1"), CI.resolve_version(10_001, "0.1.1"))
        with self.assertRaisesRegex(CI.CinemaCiError, "provided together"):
            CI.resolve_version(10_001, None)
        with self.assertRaisesRegex(CI.CinemaCiError, "between"):
            CI.resolve_version(10_000, "0.1.0")
        with self.assertRaisesRegex(CI.CinemaCiError, "between"):
            CI.resolve_version(2_100_000_001, "too-late")
        with self.assertRaisesRegex(CI.CinemaCiError, "CR/LF"):
            CI.resolve_version(10_001, "0.1.3\ninjected")


class SigningTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.project = self.root / "project"
        self.project.mkdir()
        self.sdk = self.root / "sdk"
        self.sdk.mkdir()
        self.keystore_dir = self.root / "signing"
        self.environment = {
            "CINEMA_KEYSTORE_BASE64": base64.b64encode(b"fixture-keystore").decode(),
            "CINEMA_KEY_ALIAS": "release",
            "CINEMA_STORE_PASSWORD": "store-pass",
            "CINEMA_KEY_PASSWORD": "key-pass",
            "CINEMA_CERT_SHA256": "ab" * 32,
        }

    def test_missing_secrets_fail_before_writing_any_material(self):
        with self.assertRaisesRegex(CI.CinemaCiError, "CINEMA_KEYSTORE_BASE64"):
            CI.write_signing_config(self.project, {}, self.sdk, self.keystore_dir)
        self.assertFalse((self.project / "local.properties").exists())
        self.assertFalse(self.keystore_dir.exists())

    def test_existing_keystore_and_private_properties_are_written_without_echo(self):
        result = CI.write_signing_config(
            self.project, self.environment, self.sdk, self.keystore_dir
        )
        self.assertEqual(b"fixture-keystore", result["keystore"].read_bytes())
        self.assertEqual(0o600, stat.S_IMODE(result["keystore"].stat().st_mode))
        properties = (self.project / "local.properties").read_text()
        self.assertIn(f"storeFile={result['keystore']}", properties)
        self.assertIn(f"sdk.dir={self.sdk.resolve()}", properties)
        self.assertIn("keyAlias=release", properties)
        self.assertIn("storePassword=store-pass", properties)
        self.assertIn("keyPassword=key-pass", properties)
        self.assertEqual(
            0o600,
            stat.S_IMODE((self.project / "local.properties").stat().st_mode),
        )
        self.assertNotIn(self.environment["CINEMA_KEYSTORE_BASE64"], properties)

    def test_invalid_base64_is_rejected(self):
        self.environment["CINEMA_KEYSTORE_BASE64"] = "***"
        with self.assertRaisesRegex(CI.CinemaCiError, "valid base64"):
            CI.write_signing_config(
                self.project, self.environment, self.sdk, self.keystore_dir
            )

    def test_certificate_fingerprint_is_required(self):
        del self.environment["CINEMA_CERT_SHA256"]
        with self.assertRaisesRegex(CI.CinemaCiError, "CINEMA_CERT_SHA256"):
            CI.write_signing_config(
                self.project, self.environment, self.sdk, self.keystore_dir
            )


class ApkInspectionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    @staticmethod
    def elf_header(machine=40, elf_class=1, data=1, magic=b"\x7fELF"):
        return (
            magic
            + bytes([elf_class, data, 1])
            + b"\0" * 9
            + b"\0\0"
            + machine.to_bytes(2, "little")
        )

    def write_apk(self, name="fixture.apk", abi="armeabi-v7a", dex=True, native=None):
        path = self.root / name
        native = native if native is not None else self.elf_header()
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"\x03\x00\x08\x00fixture")
            if dex:
                archive.writestr("classes.dex", b"dex\n035\x00fixture")
            archive.writestr(f"lib/{abi}/libfixture.so", native)
        return path

    def test_inspects_binary_manifest_dex_and_single_armv7_payload(self):
        path = self.write_apk()
        result = CI.inspect_apk_zip(path)
        self.assertEqual(path.stat().st_size, result["size"])
        self.assertEqual(CI.sha256_file(path), result["sha256"])

    def test_verifies_classic_and_sdk37_certificate_output(self):
        apk = self.write_apk()
        certificate = "a1" * 32
        badging = "package: name='com.mjnhmd.tvcinema' versionCode='10003' versionName='0.1.3'\nminSdkVersion:'24'\n"
        for label in ("Signer #1", "V2 Signer:", "V3 Signer:"):
            signature = label + " certificate SHA-256 digest: " + certificate + "\n"
            with self.subTest(label=label), patch.object(CI, "build_tools", return_value=self.root), patch.object(
                    CI, "run_tool", side_effect=[badging, signature]):
                result = CI.verify_apk(apk, self.root, certificate, 10003, "0.1.3")
                self.assertEqual(certificate, result["certificate_sha256"])

    def test_rejects_additional_untrusted_signer_certificates(self):
        apk = self.write_apk()
        certificate = "a1" * 32
        badging = "package: name='com.mjnhmd.tvcinema' versionCode='10003' versionName='0.1.3'\nminSdkVersion:'24'\n"
        signature = "V2 Signer: certificate SHA-256 digest: " + certificate + "\n"
        signature += "V3 Signer: certificate SHA-256 digest: " + "b2" * 32 + "\n"
        with patch.object(CI, "build_tools", return_value=self.root), patch.object(
                CI, "run_tool", side_effect=[badging, signature]), self.assertRaisesRegex(
                CI.CinemaCiError, "does not match"):
            CI.verify_apk(apk, self.root, certificate, 10003, "0.1.3")

    def test_audits_native_assets_outside_the_lib_directory(self):
        apk = self.write_apk()
        with zipfile.ZipFile(apk, "a") as archive:
            archive.writestr("assets/libmitv.so", self.elf_header(elf_class=2))
        with self.assertRaisesRegex(CI.CinemaCiError, "ELF32"):
            CI.inspect_apk_zip(apk)

    def test_audits_python_native_libraries_inside_imy_archives(self):
        content = io.BytesIO()
        with zipfile.ZipFile(content, "w") as archive:
            archive.writestr("module.so", self.elf_header(machine=183))
        apk = self.write_apk()
        with zipfile.ZipFile(apk, "a") as archive:
            archive.writestr("assets/chaquopy/requirements-armeabi-v7a.imy", content.getvalue())
        with self.assertRaisesRegex(CI.CinemaCiError, "EM_ARM"):
            CI.inspect_apk_zip(apk)

    def test_rejects_wrong_abi_missing_dex_and_non_apk(self):
        with self.assertRaisesRegex(CI.CinemaCiError, "native paths"):
            CI.inspect_apk_zip(self.write_apk("arm64.apk", abi="arm64-v8a"))
        with self.assertRaisesRegex(CI.CinemaCiError, "classes.dex"):
            CI.inspect_apk_zip(self.write_apk("nodex.apk", dex=False))
        path = self.root / "html.apk"
        path.write_text("<html>not an apk</html>")
        with self.assertRaisesRegex(CI.CinemaCiError, "not a ZIP"):
            CI.inspect_apk_zip(path)

    def test_rejects_non_elf_wrong_class_endianness_and_machine(self):
        cases = (
            ("notelf", self.elf_header(magic=b"BAD!")),
            ("elf64", self.elf_header(elf_class=2)),
            ("bigendian", self.elf_header(data=2)),
            ("arm64", self.elf_header(machine=183)),
        )
        expected = ("not ELF", "ELF32", "little-endian", "EM_ARM")
        for (name, header), message in zip(cases, expected):
            with self.subTest(name=name), self.assertRaisesRegex(
                CI.CinemaCiError, message
            ):
                CI.inspect_apk_zip(self.write_apk(name + ".apk", native=header))

    def test_parses_badging_and_rejects_a_debuggable_apk(self):
        text = (
            "package: name='com.mjnhmd.tvcinema' versionCode='10001' "
            "versionName='0.1.1' compileSdkVersion='37'\n"
            "minSdkVersion:'24'\n"
            "application-label:'TV Cinema'\n"
        )
        info = CI.parse_badging(text)
        self.assertEqual("com.mjnhmd.tvcinema", info["package"])
        self.assertEqual(10_001, info["version_code"])
        self.assertEqual("0.1.1", info["version_name"])
        self.assertEqual(24, info["min_sdk"])
        self.assertFalse(info["debuggable"])
        self.assertTrue(CI.parse_badging(text + "application-debuggable\n")["debuggable"])


class ManifestTest(unittest.TestCase):
    def metadata(self, **overrides):
        value = {
            "package": CI.CINEMA_PACKAGE,
            "version_code": 10_001,
            "version_name": "0.1.1",
            "size": 1234,
            "sha256": "ab" * 32,
        }
        value.update(overrides)
        return value

    def test_single_armv7_manifest_uses_cinema_package_and_tag_pinned_asset(self):
        result = CI.manifest_payload(
            self.metadata(),
            CI.CINEMA_REPOSITORY,
            "build-37500000000-1",
            "选集与更新修复",
        )
        self.assertEqual(1, result["schema"])
        self.assertEqual("com.mjnhmd.tvcinema", result["packageName"])
        self.assertEqual("leanback", result["mode"])
        self.assertEqual({"armeabi_v7a"}, set(result["assets"]))
        self.assertEqual(
            "https://github.com/mjnhmd/tv-cinema/releases/download/"
            "build-37500000000-1/leanback-armeabi_v7a-coexist.apk",
            result["assets"]["armeabi_v7a"]["url"],
        )
        self.assertEqual("选集与更新修复", result["desc"])

    def test_rejects_foreign_repository_mutable_tag_and_wrong_version(self):
        for kwargs in (
            {"repository": "wobuhui666/TV"},
            {"tag": "latest"},
            {"tag": "build-1-0"},
        ):
            with self.subTest(kwargs=kwargs), self.assertRaises(CI.CinemaCiError):
                CI.manifest_payload(
                    self.metadata(),
                    kwargs.get("repository", CI.CINEMA_REPOSITORY),
                    kwargs.get("tag", "build-1-1"),
                    "",
                )
        with self.assertRaisesRegex(CI.CinemaCiError, "between"):
            CI.manifest_payload(
                self.metadata(version_code=10_000),
                CI.CINEMA_REPOSITORY,
                "build-1-1",
                "",
            )

    def test_local_version_release_is_bound_to_its_apk_name(self):
        result = CI.manifest_payload(self.metadata(version_code=10003, version_name="0.1.3"),
                                     CI.CINEMA_REPOSITORY, "v0.1.3", "本地签名版")
        self.assertIn("/v0.1.3/", result["assets"]["armeabi_v7a"]["url"])
        with self.assertRaisesRegex(CI.CinemaCiError, "match"):
            CI.manifest_payload(self.metadata(version_code=10003, version_name="0.1.3"),
                                CI.CINEMA_REPOSITORY, "v0.1.4", "")

    def test_stage_cli_uses_a_notes_file_instead_of_multiline_env(self):
        parser = CI.build_parser()
        args = parser.parse_args(
            [
                "stage",
                "--apk",
                "fixture.apk",
                "--output-dir",
                "out",
                "--tag",
                "build-1-1",
                "--desc-file",
                "notes.txt",
            ]
        )
        self.assertEqual(Path("notes.txt"), args.desc_file)
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                parser.parse_args(
                    [
                        "stage",
                        "--apk",
                        "fixture.apk",
                        "--output-dir",
                        "out",
                        "--tag",
                        "build-1-1",
                        "--desc",
                        "inline",
                        "--desc-file",
                        "notes.txt",
                    ]
                )


class WorkflowContractTest(unittest.TestCase):
    def test_workflow_is_armv7_only_explicitly_signed_and_non_publishing_on_push(self):
        text = (ROOT / ".github" / "workflows" / "cinema-armv7.yml").read_text()
        self.assertIn("branches: [fongmi]", text)
        self.assertIn("workflow_dispatch:", text)
        self.assertIn("retention-days: 7", text)
        self.assertIn("if: github.event_name == 'workflow_dispatch' && inputs.publish", text)
        self.assertIn("permissions:\n  contents: read", text)
        self.assertIn("permissions:\n      contents: write", text)
        self.assertIn("cinema-build.sh --abi armeabi-v7a", text)
        self.assertIn("build/outputs/tv-cinema-armv7.apk", text)
        self.assertIn("leanback-coexist.json", text)
        self.assertIn("leanback-armeabi_v7a-coexist.apk", text)
        self.assertIn("packages: >-", text)
        self.assertNotIn("packages: |", text)
        self.assertIn("--desc-file", text)
        self.assertNotIn("RELEASE_NOTES<<EOF", text)
        self.assertNotIn("assembleLeanbackArm64", text)
        self.assertNotIn("keytool -genkeypair", text)
        self.assertIn("--draft", text)
        self.assertIn("--draft=false --latest", text)
        for secret in CI.SIGNING_SECRETS:
            self.assertIn(secret, text)


if __name__ == "__main__":
    unittest.main()
