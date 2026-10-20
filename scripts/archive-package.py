#!/usr/bin/env python3
import argparse
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("source", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
args.output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in sorted(p for p in args.source.rglob("*") if p.is_file()):
        archive.write(path, path.relative_to(args.source.parent))
print(args.output)
