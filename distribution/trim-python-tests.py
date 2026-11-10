#!/usr/bin/env python3
"""Trim generated dependency test directories before Chaquopy hashes these assets."""

import argparse
import copy
import os
from pathlib import Path, PurePosixPath
import tempfile
import zipfile


def trim_archives(directory):
    directory = Path(directory)
    archives = sorted(directory.glob("requirements-*.imy"))
    if not archives:
        raise ValueError("No generated Python requirements archives")
    staged = []
    removed = saved = 0
    try:
        for archive in archives:
            with zipfile.ZipFile(archive) as original:
                names = original.namelist()
                if len(names) != len(set(names)) or any(
                    name.startswith("/") or ".." in PurePosixPath(name).parts for name in names
                ):
                    raise ValueError("Invalid generated archive entries")
                if original.testzip() is not None:
                    raise ValueError("Corrupt generated requirements archive")
                dropped = {name for name in names if any(
                    part in ("test", "tests") for part in PurePosixPath(name).parts[:-1]
                )}
                if not dropped:
                    continue
                descriptor, temporary = tempfile.mkstemp(prefix=archive.name + ".", suffix=".tmp", dir=directory)
                os.close(descriptor)
                output = Path(temporary)
                staged.append((archive, output))
                with zipfile.ZipFile(output, "w") as trimmed:
                    trimmed.comment = original.comment
                    for entry in original.infolist():
                        if entry.filename not in dropped:
                            trimmed.writestr(copy.copy(entry), original.read(entry),
                                             compress_type=entry.compress_type, compresslevel=9)
                with zipfile.ZipFile(output) as trimmed:
                    if trimmed.testzip() is not None or set(trimmed.namelist()) != set(names) - dropped:
                        raise ValueError("Trimmed archive failed verification")
                    for name in trimmed.namelist():
                        if trimmed.read(name) != original.read(name):
                            raise ValueError("Runtime dependency changed during trimming")
                removed += len(dropped)
                saved += archive.stat().st_size - output.stat().st_size
        # Validate every archive and retained payload before replacing any output.
        for archive, temporary in staged:
            os.replace(temporary, archive)
        return removed, saved
    finally:
        for _, temporary in staged:
            temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    removed, saved = trim_archives(args.directory)
    print(f"PASS: removed {removed} dependency test entries; saved {saved} bytes")
