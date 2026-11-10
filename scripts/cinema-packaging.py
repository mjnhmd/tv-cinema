#!/usr/bin/env python3
"""Reject native debug payloads, dependency tests and stale Chaquopy asset hashes."""

import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import struct
import zipfile


def debug_sections(data):
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[5] != 1 or data[4] not in (1, 2):
        raise ValueError("Invalid little-endian ELF")
    is64 = data[4] == 2
    offset = struct.unpack_from("<Q" if is64 else "<I", data, 40 if is64 else 32)[0]
    size, count, names_index = struct.unpack_from("<HHH", data, 58 if is64 else 46)
    entry_format = "<IIQQQQIIQQ" if is64 else "<IIIIIIIIII"
    if (size < struct.calcsize(entry_format) or not 0 < names_index < count
            or offset + size * count > len(data)):
        raise ValueError("Invalid ELF section table")
    sections = [struct.unpack_from(entry_format, data, offset + i * size) for i in range(count)]
    names = sections[names_index]
    table = data[names[4]:names[4] + names[5]]
    result = []
    for section in sections:
        start = section[0]
        if start >= len(table) or b"\0" not in table[start:]:
            raise ValueError("Invalid ELF section name")
        name = table[start:table.index(b"\0", start)].decode("ascii")
        if name.startswith((".debug", ".zdebug")) or name in (".symtab", ".strtab"):
            result.append(name)
    return result


def verify_packaging(path, max_bytes=None):
    path = Path(path)
    if max_bytes is not None and path.stat().st_size > max_bytes:
        raise ValueError(f"APK exceeds size limit: {path.stat().st_size} > {max_bytes}")
    native = archives = 0
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if name.startswith("lib/") and name.endswith(".so"):
                if debug_sections(apk.read(name)):
                    raise ValueError("Unstripped native library: " + name)
                native += 1
            if name.startswith("assets/chaquopy/requirements-") and name.endswith(".imy"):
                with zipfile.ZipFile(io.BytesIO(apk.read(name))) as requirements:
                    if requirements.testzip() is not None:
                        raise ValueError("Corrupt Python dependency archive")
                    for entry in requirements.namelist():
                        if any(part in ("test", "tests") for part in PurePosixPath(entry).parts[:-1]):
                            raise ValueError("Python dependency test entry: " + entry)
                archives += 1
        if not native or not archives:
            raise ValueError("Missing expected native/Python payload")
        build = json.loads(apk.read("assets/chaquopy/build.json"))
        for name, expected in build["assets"].items():
            actual = hashlib.sha1(apk.read("assets/chaquopy/" + name)).hexdigest()
            if actual != expected:
                raise ValueError("Chaquopy asset hash mismatch: " + name)
    return native, archives


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--max-bytes", type=int)
    args = parser.parse_args()
    native, archives = verify_packaging(args.apk, args.max_bytes)
    print(f"PASS: {native} stripped native libraries, {archives} test-free dependency archives, matching asset hashes")
