#!/usr/bin/env python3
"""Deterministic Cinema ARMv7 CI helpers.

This module deliberately keeps CI-only orchestration out of the production
build script. It never creates a signing key: the keystore must come from the
existing repository secret, and the expected certificate fingerprint must be
provided separately so the CI artifact can be tied to the local release key.
"""

import argparse
import base64
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from zipfile import BadZipFile, ZipFile, is_zipfile


PROJECT_ROOT = Path(__file__).resolve().parent.parent
CINEMA_PACKAGE = "com.mjnhmd.tvcinema"
CINEMA_REPOSITORY = "mjnhmd/tv-cinema"
MODE = "leanback"
ABI_KEY = "armeabi_v7a"
ANDROID_ABI = "armeabi-v7a"
APK_ASSET = "leanback-armeabi_v7a-coexist.apk"
MANIFEST_ASSET = "leanback-coexist.json"
MIN_VERSION_CODE = 10_001
MAX_VERSION_CODE = 2_100_000_000
BASE_VERSION_NAME = "0.1.8"
VERSION_EPOCH = datetime(2020, 1, 1, tzinfo=timezone.utc)
TAG_PATTERN = re.compile(r"(?:build-[1-9][0-9]*(?:-[1-9][0-9]*)?|v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))")
CERT_PATTERN = re.compile(
    r"^[^\n]*certificate SHA-256 digest:[ \t]*([0-9a-fA-F:]+)[ \t]*$",
    re.MULTILINE,
)
SIGNING_SECRETS = (
    "CINEMA_KEYSTORE_BASE64",
    "CINEMA_KEY_ALIAS",
    "CINEMA_STORE_PASSWORD",
    "CINEMA_KEY_PASSWORD",
    "CINEMA_CERT_SHA256",
)


class CinemaCiError(RuntimeError):
    """A CI precondition failed; callers should print a bounded BLOCKED line."""


def fail(message):
    raise CinemaCiError(message)


def normalize_sha256(value):
    if not isinstance(value, str):
        fail("CINEMA_CERT_SHA256 is missing")
    normalized = re.sub(r"[^0-9a-fA-F]", "", value).lower()
    if len(normalized) != 64:
        fail("CINEMA_CERT_SHA256 must be exactly 64 hexadecimal characters")
    return normalized


def validate_version(code, name):
    if type(code) is not int or code < MIN_VERSION_CODE or code > MAX_VERSION_CODE:
        fail(
            "version code must be an integer between "
            f"{MIN_VERSION_CODE} and {MAX_VERSION_CODE}"
        )
    if not isinstance(name, str) or not name.strip():
        fail("version name must be a non-empty string")
    if "\r" in name or "\n" in name:
        fail("version name must not contain CR/LF")
    return code, name.strip()


def release_version(started_at=None):
    if started_at:
        instant = datetime.fromisoformat(started_at.replace("Z", "+00:00"))
    else:
        instant = datetime.now(timezone.utc)
    if instant.tzinfo is None:
        fail("build start time must include a UTC offset")
    instant = instant.astimezone(timezone.utc)
    code = int((instant - VERSION_EPOCH).total_seconds()) + 1_000_000
    if not MIN_VERSION_CODE <= code <= MAX_VERSION_CODE:
        fail("build time produces an unsupported Android version code")
    return validate_version(
        code, BASE_VERSION_NAME + "+" + instant.strftime("%Y%m%d.%H%M%S")
    )


def resolve_version(code=None, name=None, started_at=None):
    if (code is None) != (name is None):
        fail("--version-code and --version-name must be provided together")
    if code is None:
        return release_version(started_at)
    return validate_version(int(code), name)


def sdk_directory(value=None):
    root = value or os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not root:
        fail("Android SDK is not configured (ANDROID_SDK_ROOT/ANDROID_HOME)")
    path = Path(root).expanduser().resolve()
    if not path.is_dir():
        fail(f"Android SDK directory does not exist: {path}")
    return path


def _version_key(path):
    return tuple(int(part) for part in re.findall(r"\d+", path.name))


def build_tools(sdk):
    root = Path(sdk) / "build-tools"
    if not root.is_dir():
        fail(f"Android Build Tools directory is missing: {root}")
    candidates = [
        folder
        for folder in root.iterdir()
        if (folder / "aapt2").is_file() and (folder / "apksigner").is_file()
    ]
    if not candidates:
        fail("Android SDK has no Build Tools containing aapt2 and apksigner")
    return max(candidates, key=_version_key)


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_native_header(header, name):
    if len(header) < 20:
        fail(f"APK native library is too short: {name}")
    if header[:4] != b"\x7fELF":
        fail(f"APK native library is not ELF: {name}")
    if header[4] != 1:
        fail(f"APK native library is not ELF32: {name}")
    if header[5] != 1:
        fail(f"APK native library is not little-endian: {name}")
    machine = int.from_bytes(header[18:20], "little")
    if machine != 40:
        fail(f"APK native library is not EM_ARM (machine={machine}): {name}")


def inspect_apk_zip(path):
    path = Path(path)
    if not path.is_file():
        fail(f"APK is missing: {path}")
    try:
        with ZipFile(path) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                fail(f"APK has duplicate ZIP entries: {path.name}")
            if archive.testzip() is not None:
                fail(f"APK ZIP is corrupt: {path.name}")
            if "AndroidManifest.xml" not in names:
                fail(f"APK has no AndroidManifest.xml: {path.name}")
            if not archive.read("AndroidManifest.xml").startswith(b"\x03\x00\x08\x00"):
                fail(f"APK has no binary Android manifest: {path.name}")
            if "classes.dex" not in names:
                fail(f"APK has no classes.dex: {path.name}")
            if not archive.read("classes.dex").startswith(b"dex\n"):
                fail(f"APK has no Android bytecode: {path.name}")
            native = [name for name in names if name.startswith("lib/")]
            if not native:
                fail(f"APK has no native libraries: {path.name}")
            expected_prefix = f"lib/{ANDROID_ABI}/"
            unexpected = [
                name
                for name in native
                if not name.startswith(expected_prefix) or not name.endswith(".so")
            ]
            if unexpected:
                fail(
                    "APK contains native paths outside "
                    f"{expected_prefix}: {', '.join(sorted(unexpected)[:5])}"
                )
            payload_count = 0
            for name in names:
                if name.endswith(".so"):
                    with archive.open(name) as stream:
                        verify_native_header(stream.read(20), name)
                    payload_count += 1
                elif name.endswith(".imy"):
                    content = io.BytesIO(archive.read(name))
                    if is_zipfile(content):
                        with ZipFile(content) as nested:
                            for member in nested.namelist():
                                if member.endswith(".so"):
                                    with nested.open(member) as stream:
                                        verify_native_header(stream.read(20), name + ":" + member)
                                    payload_count += 1
    except BadZipFile as error:
        fail(f"APK is not a ZIP archive: {path.name} ({error})")
    return {
        "size": path.stat().st_size,
        "sha256": sha256_file(path),
        "native_payload_count": payload_count,
    }


def parse_badging(text):
    package = re.search(r"^package: name='([^']+)'", text, re.MULTILINE)
    version_code = re.search(r"\bversionCode='([0-9]+)'", text)
    version_name = re.search(r"\bversionName='([^']*)'", text)
    min_sdk = re.search(
        r"^(?:minSdkVersion|sdkVersion):'([0-9]+)'", text, re.MULTILINE
    )
    if not package or not version_code or not version_name or not min_sdk:
        fail("aapt2 badging output is missing package, version or minSdk")
    return {
        "package": package.group(1),
        "version_code": int(version_code.group(1)),
        "version_name": version_name.group(1),
        "min_sdk": int(min_sdk.group(1)),
        "debuggable": "application-debuggable" in text,
    }


def run_tool(arguments):
    result = subprocess.run(
        [str(argument) for argument in arguments],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode:
        detail = (result.stderr or result.stdout or "").strip()
        if len(detail) > 1200:
            detail = detail[:1200] + "..."
        fail(
            "tool failed: "
            + " ".join(str(argument) for argument in arguments[:2])
            + (f"\n{detail}" if detail else "")
        )
    return result.stdout


def verify_apk(path, sdk, cert_sha256, expected_code=None, expected_name=None):
    path = Path(path)
    metadata = inspect_apk_zip(path)
    tools = build_tools(sdk)
    info = parse_badging(
        run_tool([tools / "aapt2", "dump", "badging", path])
    )
    if info["package"] != CINEMA_PACKAGE:
        fail(f"unexpected package name: {info['package']}")
    if info["min_sdk"] > 28:
        fail(f"minSdk {info['min_sdk']} does not support Android 9")
    if info["debuggable"]:
        fail("release APK is debuggable")
    if expected_code is not None and info["version_code"] != int(expected_code):
        fail(
            f"APK versionCode {info['version_code']} does not match "
            f"requested {expected_code}"
        )
    if expected_name is not None and info["version_name"] != expected_name:
        fail(
            f"APK versionName {info['version_name']!r} does not match "
            f"requested {expected_name!r}"
        )
    signature = run_tool([tools / "apksigner", "verify", "--print-certs", path])
    fingerprints = {normalize_sha256(value) for value in CERT_PATTERN.findall(signature)}
    if not fingerprints:
        fail("apksigner did not report a signer certificate")
    expected = normalize_sha256(cert_sha256)
    if fingerprints != {expected}:
        fail("APK signer certificate does not match CINEMA_CERT_SHA256")
    return {
        **metadata,
        **info,
        "certificate_sha256": expected,
    }


def manifest_payload(apk_metadata, repository, tag, description):
    if repository != CINEMA_REPOSITORY:
        fail(
            "OTA manifest repository must be "
            f"{CINEMA_REPOSITORY}; got {repository!r}"
        )
    if not TAG_PATTERN.fullmatch(tag or ""):
        fail("OTA tag must be an immutable build-<run_id>[-<attempt>] or version tag")
    if apk_metadata.get("package") != CINEMA_PACKAGE:
        fail("OTA manifest package does not match Cinema")
    code = apk_metadata.get("version_code")
    name = apk_metadata.get("version_name")
    validate_version(code, name)
    if tag.startswith("v") and tag[1:] != name:
        fail("version release tag must match the APK version name")
    return {
        "schema": 1,
        "code": code,
        "name": name,
        "desc": description or "",
        "packageName": CINEMA_PACKAGE,
        "mode": MODE,
        "assets": {
            ABI_KEY: {
                "url": (
                    f"https://github.com/{CINEMA_REPOSITORY}/releases/download/"
                    f"{tag}/{APK_ASSET}"
                ),
                "size": apk_metadata["size"],
                "sha256": apk_metadata["sha256"],
            }
        },
    }


def write_signing_config(project_root, environment, sdk, keystore_dir):
    missing = [name for name in SIGNING_SECRETS if not environment.get(name)]
    if missing:
        fail(
            "missing required signing secrets: "
            + ", ".join(missing)
            + "; configure the existing release keystore, never generate a CI key"
        )
    for name in ("CINEMA_KEY_ALIAS", "CINEMA_STORE_PASSWORD", "CINEMA_KEY_PASSWORD"):
        if "\n" in environment[name] or "\r" in environment[name]:
            fail(f"{name} must not contain a newline")
    certificate = normalize_sha256(environment["CINEMA_CERT_SHA256"])
    encoded = "".join(environment["CINEMA_KEYSTORE_BASE64"].split())
    try:
        keystore_bytes = base64.b64decode(encoded, validate=True)
    except Exception:
        fail("CINEMA_KEYSTORE_BASE64 is not valid base64")
    if not keystore_bytes:
        fail("CINEMA_KEYSTORE_BASE64 decoded to an empty file")

    project_root = Path(project_root).resolve()
    sdk = Path(sdk).expanduser().resolve()
    if not sdk.is_dir():
        fail(f"Android SDK directory does not exist: {sdk}")
    keystore_dir = Path(keystore_dir).expanduser().resolve()
    keystore_dir.mkdir(parents=True, exist_ok=True)
    keystore = keystore_dir / "cinema-release.jks"
    keystore.write_bytes(keystore_bytes)
    os.chmod(keystore, 0o600)

    local_properties = project_root / "local.properties"
    existing = {}
    if local_properties.is_file():
        for line in local_properties.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if stripped and not stripped.startswith("#") and "=" in stripped:
                key, value = stripped.split("=", 1)
                existing[key.strip()] = value.strip()
    managed = {
        "sdk.dir": str(sdk),
        "storeFile": str(keystore),
        "keyAlias": environment["CINEMA_KEY_ALIAS"],
        "storePassword": environment["CINEMA_STORE_PASSWORD"],
        "keyPassword": environment["CINEMA_KEY_PASSWORD"],
    }
    lines = [
        f"{key}={value}"
        for key, value in existing.items()
        if key not in managed
    ]
    lines.extend(f"{key}={value}" for key, value in managed.items())
    local_properties.write_text("\n".join(lines) + "\n", encoding="utf-8")
    os.chmod(local_properties, 0o600)
    return {
        "keystore": keystore,
        "local_properties": local_properties,
        "certificate_sha256": certificate,
    }


def stage_release(
    apk,
    output_dir,
    sdk,
    certificate_sha256,
    repository,
    tag,
    description,
    expected_code=None,
    expected_name=None,
    include_manifest=False,
):
    if (expected_code is None) != (expected_name is None):
        fail("expected version code and name must be provided together")
    if expected_code is not None:
        validate_version(expected_code, expected_name)
    metadata = verify_apk(
        apk,
        sdk,
        certificate_sha256,
        expected_code=expected_code,
        expected_name=expected_name,
    )
    output_dir = Path(output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    apk_target = output_dir / APK_ASSET
    manifest_target = output_dir / MANIFEST_ASSET
    for target in (apk_target, manifest_target):
        if target.exists():
            target.unlink()
    shutil.copy2(apk, apk_target)
    if sha256_file(apk_target) != metadata["sha256"]:
        fail("copied APK checksum changed during staging")

    payload = None
    if include_manifest:
        payload = manifest_payload(metadata, repository, tag, description)
        manifest_target.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return apk_target, manifest_target if payload is not None else None, payload


def command_version(args):
    code, name = resolve_version(
        args.version_code, args.version_name, args.started_at
    )
    print(f"TV_VERSION_CODE={code}")
    print(f"TV_VERSION_NAME={name}")


def command_signing(args):
    sdk = sdk_directory(args.sdk_dir)
    write_signing_config(
        args.project_root, os.environ, sdk, args.keystore_dir
    )
    print("Signing material configured for the existing release key")


def command_verify(args):
    if (args.version_code is None) != (args.version_name is None):
        fail("--version-code and --version-name must be provided together")
    if args.version_code is not None:
        validate_version(args.version_code, args.version_name)
    metadata = verify_apk(
        args.apk,
        sdk_directory(args.sdk),
        args.cert_sha256 or os.environ.get("CINEMA_CERT_SHA256"),
        expected_code=args.version_code,
        expected_name=args.version_name,
    )
    print(
        "PASS: package={package} code={version_code} name={version_name} "
        "minSdk={min_sdk} abi={abi} certificate={certificate_sha256}".format(
            abi=ANDROID_ABI, **metadata
        )
    )


def command_stage(args):
    if (args.version_code is None) != (args.version_name is None):
        fail("--version-code and --version-name must be provided together")
    if args.version_code is not None:
        validate_version(args.version_code, args.version_name)
    description = args.desc
    if args.desc_file is not None:
        try:
            description = Path(args.desc_file).read_text(encoding="utf-8")
        except OSError as error:
            fail(f"cannot read --desc-file: {error}")
    apk, manifest, payload = stage_release(
        args.apk,
        args.output_dir,
        sdk_directory(args.sdk),
        args.cert_sha256 or os.environ.get("CINEMA_CERT_SHA256"),
        args.repository,
        args.tag,
        description,
        expected_code=args.version_code,
        expected_name=args.version_name,
        include_manifest=args.manifest,
    )
    print(f"PASS: staged APK {apk}")
    if manifest is not None:
        print(f"PASS: staged OTA manifest {manifest}")
        print(
            "Manifest asset URL is tag-pinned and is not a live release URL "
            "until the corresponding GitHub Release exists."
        )


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    version = commands.add_parser("version", help="print TV_VERSION_* for GITHUB_ENV")
    version.add_argument("--version-code", type=int)
    version.add_argument("--version-name")
    version.add_argument("--started-at")
    version.set_defaults(handler=command_version)

    signing = commands.add_parser("signing", help="decode the existing keystore and write local.properties")
    signing.add_argument("--project-root", type=Path, default=PROJECT_ROOT)
    signing.add_argument("--sdk-dir")
    signing.add_argument(
        "--keystore-dir",
        default=str(
            Path(os.environ.get("RUNNER_TEMP") or tempfile.gettempdir())
            / "cinema-signing"
        ),
    )
    signing.set_defaults(handler=command_signing)

    verify = commands.add_parser("verify-apk", help="check package, version, ABI and signature")
    verify.add_argument("--apk", type=Path, required=True)
    verify.add_argument("--sdk")
    verify.add_argument("--cert-sha256")
    verify.add_argument("--version-code", type=int)
    verify.add_argument("--version-name")
    verify.set_defaults(handler=command_verify)

    stage = commands.add_parser("stage", help="stage the APK and optional single-ABI OTA manifest")
    stage.add_argument("--apk", type=Path, required=True)
    stage.add_argument("--output-dir", type=Path, required=True)
    stage.add_argument("--sdk")
    stage.add_argument("--cert-sha256")
    stage.add_argument("--repository", default=CINEMA_REPOSITORY)
    stage.add_argument("--tag", required=True)
    description = stage.add_mutually_exclusive_group()
    description.add_argument("--desc", default="")
    description.add_argument("--desc-file", type=Path)
    stage.add_argument("--version-code", type=int)
    stage.add_argument("--version-name")
    stage.add_argument("--manifest", action="store_true")
    stage.set_defaults(handler=command_stage)
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        args.handler(args)
        return 0
    except CinemaCiError as error:
        print(f"BLOCKED: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
