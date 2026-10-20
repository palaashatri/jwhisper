#!/usr/bin/env python3
import argparse
import hashlib
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("directory", type=Path)
args = parser.parse_args()
root = args.directory
files = sorted(p for p in root.rglob("*") if p.is_file() and p.name != "SHA256SUMS")
lines = []
for path in files:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    lines.append(f"{digest.hexdigest()}  {path.relative_to(root).as_posix()}")
(root / "SHA256SUMS").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(root / "SHA256SUMS")
