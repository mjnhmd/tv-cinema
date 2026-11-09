#!/usr/bin/env python3
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys


PROJECT_ROOT = Path(__file__).resolve().parent.parent
BASELINE_PATH = PROJECT_ROOT / "distribution" / "baseline.json"
STATE_PATH = PROJECT_ROOT / "build" / "tv-cinema" / "generated-state.json"


class PrepareError(RuntimeError):
    pass


def run_git(repository, *arguments):
    return subprocess.run(
        ["git", "-C", str(repository), *arguments],
        capture_output=True,
        text=True,
        check=False,
    )


def checked_git(repository, *arguments):
    result = run_git(repository, *arguments)
    if result.returncode:
        raise PrepareError(result.stderr.strip() or "Git command failed")
    return result.stdout.strip()


def load_baseline():
    try:
        return json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise PrepareError(f"Cannot read {BASELINE_PATH}: {error}") from error


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            chunk = stream.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def ensure_checkout(specification, label):
    checkout = PROJECT_ROOT / specification["directory"]
    commit = specification["commit"]
    if not checkout.exists():
        checkout.parent.mkdir(parents=True, exist_ok=True)
        result = subprocess.run(
            [
                "git",
                "clone",
                "--no-checkout",
                "--depth",
                "1",
                "--branch",
                specification["branch"],
                specification["repository"],
                str(checkout),
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode:
            raise PrepareError(f"Cannot clone {label}: {result.stderr.strip()}")
        checked_git(checkout, "fetch", "--depth", "1", "origin", commit)
        checked_git(checkout, "checkout", "--detach", commit)
    if not (checkout / ".git").exists():
        raise PrepareError(f"{label} checkout is not a Git repository: {checkout}")
    head = checked_git(checkout, "rev-parse", "HEAD")
    if head != commit:
        raise PrepareError(
            f"{label} HEAD is {head}; pinned commit is {commit}. "
            "Refusing to reset, checkout, or clean an existing checkout."
        )
    return checkout


def verify_native_archives(tv_directory, baseline):
    for archive_name, expected_hash in baseline["nativeArchives"].items():
        archive = tv_directory / archive_name
        if not archive.is_file():
            raise PrepareError(f"Missing native archive: {archive}")
        actual_hash = sha256(archive)
        if actual_hash != expected_hash:
            raise PrepareError(
                f"Native archive hash mismatch: {archive_name} "
                f"expected {expected_hash}, actual {actual_hash}"
            )


def install_media_settings(tv_directory, media_directory):
    source = tv_directory / ".github" / "media3-composite-settings.gradle.kts"
    target = media_directory / "settings.gradle.kts"
    if not source.is_file():
        raise PrepareError(f"Missing paired Media3 settings template: {source}")
    expected = source.read_bytes()
    if target.exists():
        if target.read_bytes() == expected:
            return "Media3 settings already match the paired template"
        original = run_git(media_directory, "show", "HEAD:settings.gradle.kts")
        if original.returncode or target.read_bytes() != original.stdout.encode("utf-8"):
            raise PrepareError(
                f"Media3 settings have unexpected edits. Refusing to overwrite {target}."
            )
    target.write_bytes(expected)
    return "Installed paired Media3 settings"


def install_github_stub_link(reference_directory, tv_directory):
    link = reference_directory / ".github"
    desired = Path(tv_directory.name) / ".github"
    if link.is_symlink():
        if link.resolve() == (tv_directory / ".github").resolve():
            return "Reference .github stub link already matches the paired TV checkout"
        raise PrepareError(
            f"{link} points to {os.readlink(link)}; expected {desired}. "
            "Refusing to overwrite an unexpected stub path."
        )
    if link.exists():
        raise PrepareError(f"{link} exists but is not the expected symlink")
    os.symlink(desired, link)
    return "Created reference .github stub link"


def apply_media_patch(tv_directory, media_directory):
    patch_script = tv_directory / "third_party" / "media3" / "apply_patches.py"
    if not patch_script.is_file():
        raise PrepareError(f"Missing reviewed Media3 patch runner: {patch_script}")
    result = subprocess.run(
        [sys.executable, str(patch_script), str(media_directory)],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode:
        raise PrepareError(result.stderr.strip() or result.stdout.strip())
    return result.stdout.strip()


def load_state():
    if not STATE_PATH.is_file():
        return {}
    try:
        return json.loads(STATE_PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise PrepareError(f"Cannot read generated state {STATE_PATH}: {error}") from error


def save_state(state):
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    STATE_PATH.write_text(
        json.dumps(state, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def apply_copies(tv_directory, baseline, state):
    messages = []
    for entry in baseline["copies"]:
        source = PROJECT_ROOT / entry["source"]
        target = tv_directory / entry["target"]
        if not source.is_file():
            raise PrepareError(f"Missing Cinema overlay source: {source}")
        expected = source.read_bytes()
        state_key = entry["target"]
        if target.exists():
            current = target.read_bytes()
            if current != expected:
                tracked = run_git(tv_directory, "ls-files", "--error-unmatch", state_key)
                if tracked.returncode == 0:
                    raise PrepareError(
                        f"Refusing to overwrite pinned baseline file {state_key}; "
                        "move the Cinema change into distribution/cinema.patch."
                    )
                previous_hash = state.get(state_key)
                if previous_hash != sha256(target):
                    raise PrepareError(
                        f"Managed overlay target changed unexpectedly: {state_key}. "
                        "Update distribution/overlay and rerun prepare with a matching generated state."
                    )
                target.write_bytes(expected)
                messages.append(f"Updated managed overlay {state_key}")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(expected)
            messages.append(f"Copied overlay {state_key}")
        state[state_key] = sha256(target)
    return messages


def parse_properties(path):
    values = {}
    order = []
    if not path.is_file():
        return values, order
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            order.append(("raw", line))
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
        order.append(("key", key.strip()))
    return values, order


def write_reference_properties(root_properties, reference_properties):
    root_values, _ = parse_properties(root_properties)
    required = ("sdk.dir", "storeFile", "keyAlias", "storePassword")
    missing = [key for key in required if not root_values.get(key)]
    if missing:
        raise PrepareError(
            f"Missing local.properties keys: {', '.join(missing)}. "
            "Configure root local.properties before preparing the production build."
        )
    store_file = Path(root_values["storeFile"]).expanduser()
    if not store_file.is_absolute():
        store_file = PROJECT_ROOT / "app" / store_file
    store_file = store_file.resolve()
    if not store_file.is_file():
        raise PrepareError(f"Signing store does not exist: {store_file}")
    sdk_directory = Path(root_values["sdk.dir"]).expanduser()
    if not sdk_directory.is_absolute():
        sdk_directory = PROJECT_ROOT / sdk_directory
    sdk_directory = sdk_directory.resolve()
    if not sdk_directory.is_dir():
        raise PrepareError(f"Android SDK directory does not exist: {sdk_directory}")
    desired = {
        "sdk.dir": str(sdk_directory),
        "storeFile": str(store_file),
        "keyAlias": root_values["keyAlias"],
        "storePassword": root_values["storePassword"],
        "keyPassword": root_values.get("keyPassword") or root_values["storePassword"],
    }
    existing, order = parse_properties(reference_properties)
    if all(existing.get(key) == value for key, value in desired.items()):
        return "Reference local.properties already matches root configuration"
    merged = dict(existing)
    merged.update(desired)
    lines = []
    written = set()
    for kind, value in order:
        if kind == "raw":
            lines.append(value)
            continue
        lines.append(f"{value}={merged[value]}")
        written.add(value)
    for key in ("sdk.dir", "storeFile", "keyAlias", "storePassword", "keyPassword"):
        if key not in written:
            lines.append(f"{key}={merged[key]}")
    reference_properties.parent.mkdir(parents=True, exist_ok=True)
    reference_properties.touch(mode=0o600, exist_ok=True)
    reference_properties.chmod(0o600)
    reference_properties.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return "Updated reference local.properties without printing secrets"


def apply_cinema_patch(tv_directory, baseline):
    patch = PROJECT_ROOT / "distribution" / "cinema.patch"
    if not patch.is_file():
        raise PrepareError(
            "Missing distribution/cinema.patch. Generate and review the Cinema patch before building."
        )
    expected_hash = baseline.get("cinemaPatchSha256")
    if expected_hash and sha256(patch) != expected_hash:
        raise PrepareError(
            f"distribution/cinema.patch hash mismatch; expected {expected_hash}"
        )
    entries = re.findall(
        r"^diff --git a/(\S+) b/(\S+)\nindex ([0-9a-f]{40})\.\.([0-9a-f]{40}) 100644$",
        patch.read_text(encoding="utf-8"), re.MULTILINE,
    )
    if not entries or patch.read_text(encoding="utf-8").count("diff --git ") != len(entries):
        raise PrepareError("Cinema patch must declare full original and modified blob IDs")
    permitted = {original for original, modified, _, _ in entries if original == modified}
    changed = set(checked_git(tv_directory, "diff", "--name-only", "HEAD").splitlines())
    if changed - permitted:
        raise PrepareError("TV checkout has changes outside the reviewed Cinema patch")
    states = []
    for name, target_name, original_blob, modified_blob in entries:
        if name != target_name or checked_git(tv_directory, "rev-parse", f"HEAD:{name}") != original_blob:
            raise PrepareError(f"Cinema patch baseline mismatch: {name}")
        actual = checked_git(tv_directory, "hash-object", "--no-filters", "--", name)
        if actual not in (original_blob, modified_blob):
            raise PrepareError(f"Cinema patch target has unexpected edits: {name}")
        states.append(actual == modified_blob)
    if any(states) and not all(states):
        raise PrepareError("Cinema patch is partially applied; refusing to overwrite it")
    forward = run_git(tv_directory, "apply", "--check", str(patch))
    if forward.returncode == 0:
        applied = run_git(tv_directory, "apply", str(patch))
        if applied.returncode:
            raise PrepareError(applied.stderr.strip() or "Cinema patch application failed")
        updated = set(checked_git(tv_directory, "diff", "--name-only", "HEAD").splitlines())
        if updated - permitted:
            raise PrepareError("Cinema patch modified paths outside its reviewed manifest")
        return "Applied distribution/cinema.patch"
    reverse = run_git(tv_directory, "apply", "--reverse", "--check", str(patch))
    if reverse.returncode == 0:
        return "distribution/cinema.patch is already applied"
    raise PrepareError(
        "Cinema patch does not match the pinned checkout and is not already applied. "
        "Refusing to overwrite additional local changes."
    )


def main():
    try:
        baseline = load_baseline()
        tv_directory = ensure_checkout(baseline["tv"], "TV")
        media_directory = ensure_checkout(baseline["media"], "Media3")
        verify_native_archives(tv_directory, baseline)
        messages = [
            install_media_settings(tv_directory, media_directory),
            install_github_stub_link(PROJECT_ROOT / "build" / "reference", tv_directory),
            apply_media_patch(tv_directory, media_directory),
        ]
        state = load_state()
        messages.extend(apply_copies(tv_directory, baseline, state))
        save_state(state)
        messages.append(
            write_reference_properties(
                PROJECT_ROOT / "local.properties",
                tv_directory / "local.properties",
            )
        )
        messages.append(apply_cinema_patch(tv_directory, baseline))
        for message in messages:
            print(message)
        print("PASS: production inputs prepared")
        return 0
    except PrepareError as error:
        print(f"BLOCKED: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
