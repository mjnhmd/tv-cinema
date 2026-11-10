import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile
import struct
import json
import hashlib
import io


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("trim_python_tests", ROOT / "distribution/trim-python-tests.py")
TRIM = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TRIM)
PACK_SPEC = importlib.util.spec_from_file_location("cinema_packaging", ROOT / "scripts/cinema-packaging.py")
PACK = importlib.util.module_from_spec(PACK_SPEC)
PACK_SPEC.loader.exec_module(PACK)


class DependencyPackagingTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def archive(self, name="requirements-common.imy"):
        path = self.root / name
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.comment = b"original"
            for entry, data in {"numpy/tests/test_math.pyc": b"test" * 1024,
                                "Crypto/SelfTest/test.pyc": b"runtime helper",
                                "numpy/testing/__init__.pyc": b"public testing API",
                                "numpy/core/engine.so": b"native bytes",
                                "package/tests.pyc": b"public module",
                                "numpy-1.26.2.dist-info/METADATA": b"license and version"}.items():
                archive.writestr(entry, data)
        return path

    def test_excludes_only_test_directories_and_preserves_every_runtime_payload(self):
        path = self.archive()
        with zipfile.ZipFile(path) as archive:
            before = {name: archive.read(name) for name in archive.namelist()}
        count, _ = TRIM.trim_archives(self.root)
        self.assertEqual(1, count)
        with zipfile.ZipFile(path) as archive:
            self.assertEqual(b"original", archive.comment)
            self.assertNotIn("numpy/tests/test_math.pyc", archive.namelist())
            for name in archive.namelist():
                self.assertEqual(before[name], archive.read(name))
        after = path.read_bytes()
        self.assertEqual((0, 0), TRIM.trim_archives(self.root))
        self.assertEqual(after, path.read_bytes())

    def test_invalid_later_archive_cannot_leave_an_earlier_archive_changed(self):
        path = self.archive("requirements-a.imy")
        before = path.read_bytes()
        (self.root / "requirements-z.imy").write_bytes(b"invalid")
        with self.assertRaises(zipfile.BadZipFile):
            TRIM.trim_archives(self.root)
        self.assertEqual(before, path.read_bytes())
        self.assertEqual([], list(self.root.glob("*.tmp")))

    def test_rejects_traversal_and_requires_generated_archives(self):
        with self.assertRaisesRegex(ValueError, "No generated"):
            TRIM.trim_archives(self.root)
        path = self.root / "requirements-common.imy"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("../tests/escape.pyc", b"bad")
        original = path.read_bytes()
        with self.assertRaisesRegex(ValueError, "Invalid generated"):
            TRIM.trim_archives(self.root)
        self.assertEqual(original, path.read_bytes())


class FinalPackagingGuardTest(unittest.TestCase):
    def fixture(self, root, debug=False, tests=False, stale=False):
        data = bytearray(64)
        data[:7] = b"\x7fELF\x01\x01\x01"
        names = b"\0.shstrtab\0" + (b".debug_info\0" if debug else b".text\0")
        struct.pack_into("<I", data, 32, 64)
        struct.pack_into("<HHH", data, 46, 40, 3, 1)
        data += bytes(40)
        data += struct.pack("<IIIIIIIIII", 1, 3, 0, 0, 184, len(names), 0, 0, 1, 0)
        data += struct.pack("<IIIIIIIIII", 11, 1, 0, 0, 0, 0, 0, 0, 1, 0)
        data += names
        content = io.BytesIO()
        with zipfile.ZipFile(content, "w") as requirements:
            requirements.writestr("numpy/tests/test_math.pyc" if tests else "numpy/__init__.pyc", b"payload")
        payload = content.getvalue()
        build = {"assets": {"requirements-common.imy": "0" * 40 if stale else hashlib.sha1(payload).hexdigest()}}
        path = root / "fixture.apk"
        with zipfile.ZipFile(path, "w") as apk:
            apk.writestr("lib/armeabi-v7a/libfixture.so", data)
            apk.writestr("assets/chaquopy/requirements-common.imy", payload)
            apk.writestr("assets/chaquopy/build.json", json.dumps(build))
        return path

    def test_accepts_trimmed_payload_and_rejects_native_python_hash_and_size_regressions(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertEqual((1, 1), PACK.verify_packaging(self.fixture(root)))
            for options, message in [({"debug": True}, "Unstripped"), ({"tests": True}, "test entry"),
                                     ({"stale": True}, "hash mismatch")]:
                with self.subTest(options=options), self.assertRaisesRegex(ValueError, message):
                    PACK.verify_packaging(self.fixture(root, **options))
            with self.assertRaisesRegex(ValueError, "size limit"):
                PACK.verify_packaging(self.fixture(root), 1)
