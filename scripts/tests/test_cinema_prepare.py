import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("cinema_prepare", ROOT / "scripts/cinema-prepare.py")
PREPARE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREPARE)
VERIFY_SPEC = importlib.util.spec_from_file_location("cinema_verify", ROOT / "scripts/cinema-verify-inputs.py")
VERIFY = importlib.util.module_from_spec(VERIFY_SPEC)
VERIFY_SPEC.loader.exec_module(VERIFY)


class RemovalSafetyTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.target = self.root / "app/src/main/retired.java"
        self.target.parent.mkdir(parents=True)
        self.target.write_bytes(b"original")
        self.entry = {"path": "app/src/main/retired.java",
                      "sha256": hashlib.sha256(b"original").hexdigest()}

    def test_exact_removal_is_idempotent_and_leaves_other_files(self):
        other = self.target.with_name("ordinary-subtitle.java")
        other.write_bytes(b"keep")
        baseline = {"removals": [self.entry]}
        PREPARE.apply_removals(self.root, baseline)
        PREPARE.apply_removals(self.root, baseline)
        self.assertFalse(self.target.exists())
        self.assertEqual(b"keep", other.read_bytes())

    def test_all_targets_validate_before_any_file_is_deleted(self):
        edited = self.target.with_name("edited.java")
        edited.write_bytes(b"user changes")
        baseline = {"removals": [self.entry, dict(self.entry, path="app/src/main/edited.java")]}
        with self.assertRaisesRegex(PREPARE.PrepareError, "unexpected edits"):
            PREPARE.apply_removals(self.root, baseline)
        self.assertEqual(b"original", self.target.read_bytes())
        self.assertEqual(b"user changes", edited.read_bytes())

    def test_rejects_escapes_non_app_paths_and_invalid_checksums(self):
        entries = [dict(self.entry, path=name) for name in
                   ("", ".", "app", "/tmp/file", "app/../../../file", "app/src/../file", ".git/config", "app//file", "app\\file")]
        entries.append(dict(self.entry, sha256="bad"))
        entries.append(dict(self.entry, path=None))
        for entry in entries:
            with self.subTest(entry=entry), self.assertRaises(PREPARE.PrepareError):
                PREPARE.apply_removals(self.root, {"removals": [entry]})
        self.assertTrue(self.target.exists())

    def test_rejects_symlinks_and_duplicate_targets(self):
        link = self.target.with_name("link.java")
        link.symlink_to(self.target)
        with self.assertRaisesRegex(PREPARE.PrepareError, "symlink"):
            PREPARE.apply_removals(self.root, {"removals": [dict(self.entry, path="app/src/main/link.java")]})
        with self.assertRaisesRegex(PREPARE.PrepareError, "duplicate"):
            PREPARE.apply_removals(self.root, {"removals": [self.entry, self.entry]})
        self.assertTrue(self.target.exists())

    def test_verifier_rejects_present_target_and_accepts_removed_target(self):
        baseline = {"removals": [self.entry]}
        failures = []
        VERIFY.check_removals(self.root, baseline, failures)
        self.assertEqual(1, len(failures))
        PREPARE.apply_removals(self.root, baseline)
        failures = []
        VERIFY.check_removals(self.root, baseline, failures)
        self.assertEqual([], failures)
