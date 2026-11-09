#!/usr/bin/env python3
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import importlib.util


PROJECT_ROOT = Path(__file__).resolve().parent.parent
BASELINE_PATH = PROJECT_ROOT / "distribution" / "baseline.json"


def run_git(repository, *arguments):
    return subprocess.run(
        ["git", "-C", str(repository), *arguments],
        capture_output=True,
        text=True,
        check=False,
    )


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            chunk = stream.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def read_properties(path):
    values = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def check_checkout(specification, label, failures):
    checkout = PROJECT_ROOT / specification["directory"]
    if not checkout.is_dir() or not (checkout / ".git").exists():
        failures.append(f"{label} checkout is missing: {checkout}")
        return None
    result = run_git(checkout, "rev-parse", "HEAD")
    if result.returncode or result.stdout.strip() != specification["commit"]:
        failures.append(
            f"{label} HEAD is {result.stdout.strip() or 'unknown'}; "
            f"expected {specification['commit']}"
        )
    return checkout


def check_native_archives(tv_directory, baseline, failures):
    for archive_name, expected_hash in baseline["nativeArchives"].items():
        archive = tv_directory / archive_name
        if not archive.is_file():
            failures.append(f"Missing native archive: {archive_name}")
            continue
        actual_hash = sha256(archive)
        if actual_hash != expected_hash:
            failures.append(
                f"Native archive hash mismatch: {archive_name} "
                f"expected {expected_hash}, actual {actual_hash}"
            )


def check_media_settings(tv_directory, media_directory, failures):
    source = tv_directory / ".github" / "media3-composite-settings.gradle.kts"
    target = media_directory / "settings.gradle.kts"
    if not source.is_file():
        failures.append(f"Missing Media3 settings template: {source}")
        return
    if not target.is_file():
        failures.append(f"Missing paired Media3 settings: {target}")
        return
    if source.read_bytes() != target.read_bytes():
        failures.append("Media3 settings do not match the paired composite template")


def check_stub_link(reference_directory, tv_directory, failures):
    link = reference_directory / ".github"
    if not link.is_symlink():
        failures.append(f"Missing reference .github symlink: {link}")
        return
    if link.resolve() != (tv_directory / ".github").resolve():
        failures.append(f"Reference .github symlink has the wrong target: {os.readlink(link)}")


def check_patch(tv_directory, baseline, failures):
    patch = PROJECT_ROOT / "distribution" / "cinema.patch"
    if not patch.is_file():
        failures.append("Missing distribution/cinema.patch")
        return
    expected_hash = baseline.get("cinemaPatchSha256")
    if expected_hash and sha256(patch) != expected_hash:
        failures.append(
            f"distribution/cinema.patch hash mismatch; expected {expected_hash}"
        )
        return
    forward = run_git(tv_directory, "apply", "--check", str(patch))
    if forward.returncode == 0:
        return
    reverse = run_git(tv_directory, "apply", "--reverse", "--check", str(patch))
    if reverse.returncode != 0:
        failures.append("Cinema patch is neither applicable nor already applied")
        return
    module_spec = importlib.util.spec_from_file_location(
        "cinema_prepare_verifier", PROJECT_ROOT / "scripts" / "cinema-prepare.py"
    )
    module = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(module)
    try:
        module.apply_cinema_patch(tv_directory, baseline)
    except module.PrepareError as error:
        failures.append(str(error))


def check_copies(tv_directory, baseline, failures):
    for entry in baseline["copies"]:
        source = PROJECT_ROOT / entry["source"]
        target = tv_directory / entry["target"]
        if not source.is_file():
            failures.append(f"Missing overlay source: {entry['source']}")
            continue
        if not target.is_file():
            failures.append(f"Missing managed overlay target: {entry['target']}")
            continue
        if source.read_bytes() != target.read_bytes():
            failures.append(f"Managed overlay target differs: {entry['target']}")


def check_local_properties(tv_directory, failures):
    root_values = read_properties(PROJECT_ROOT / "local.properties")
    reference_values = read_properties(tv_directory / "local.properties")
    for key in ("sdk.dir", "storeFile", "keyAlias", "storePassword"):
        if not root_values.get(key):
            failures.append(f"Root local.properties is missing {key}")
        if not reference_values.get(key):
            failures.append(f"Reference local.properties is missing {key}")
    for key in ("keyAlias", "storePassword"):
        if root_values.get(key) != reference_values.get(key):
            failures.append(f"Reference signing field does not match root configuration: {key}")
    expected_password = root_values.get("keyPassword") or root_values.get("storePassword")
    if reference_values.get("keyPassword") != expected_password:
        failures.append("Reference signing field does not match root configuration: keyPassword")
    expected_store = Path(root_values.get("storeFile", ""))
    if not expected_store.is_absolute():
        expected_store = PROJECT_ROOT / "app" / expected_store
    if Path(reference_values.get("storeFile", "")).resolve() != expected_store.resolve():
        failures.append("Reference signing store path does not match root configuration")


def check_sdk_compat_script(failures):
    script = PROJECT_ROOT / "distribution" / "sdk-compat.init.gradle"
    if not script.is_file():
        failures.append("Missing distribution/sdk-compat.init.gradle")


def main():
    try:
        baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print(f"BLOCKED: cannot read {BASELINE_PATH}: {error}")
        return 2
    failures = []
    tv_directory = check_checkout(baseline["tv"], "TV", failures)
    media_directory = check_checkout(baseline["media"], "Media3", failures)
    if tv_directory is not None:
        check_native_archives(tv_directory, baseline, failures)
        check_stub_link(PROJECT_ROOT / "build" / "reference", tv_directory, failures)
        check_patch(tv_directory, baseline, failures)
        check_copies(tv_directory, baseline, failures)
        check_local_properties(tv_directory, failures)
    if media_directory is not None and tv_directory is not None:
        check_media_settings(tv_directory, media_directory, failures)
    check_sdk_compat_script(failures)
    if failures:
        print("BLOCKED: production inputs are not ready.")
        for failure in failures:
            print(f"  {failure}")
        return 2
    print("PASS: pinned checkouts, native hashes, patches, overlay copies and signing inputs are ready.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
