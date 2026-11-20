#!/usr/bin/env python3
"""
Shelter code audit — how much of this project is still Shelter-derived.

Usage:
    python3 tools/shelter-audit.py [path/to/shelter/source]

Prints one line per Java file with the percentage of its normalised lines that also
exist in the matching Shelter file. Files below 25% are treated as rewritten.

Exit code: 0 when no Shelter-derived file remains, 1 otherwise.
This is the gauge for the rule: "no separate repository until this reaches zero".
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MAIN = os.path.join(ROOT, 'app/src/main/java/com/nijatva/workspace')
SHELTER = sys.argv[1] if len(sys.argv) > 1 else os.environ.get('SHELTER_SRC', '')
THRESHOLD = 25  # percent


def norm(path):
    out = []
    for line in open(path, encoding='utf-8', errors='ignore'):
        s = line.strip()
        if not s or s.startswith('//') or s.startswith('*') or s.startswith('/*'):
            continue
        if s.startswith('import ') or s.startswith('package '):
            continue
        out.append(re.sub(r'\s+', ' ', s))
    return out


def main():
    if not SHELTER or not os.path.isdir(SHELTER):
        print('Give the Shelter source path: python3 tools/shelter-audit.py /path/to/Shelter-master')
        return 2
    rows, total_files, derived_files, derived_lines = [], 0, 0, 0
    for base, _, files in os.walk(MAIN):
        for f in sorted(files):
            if not f.endswith('.java'):
                continue
            total_files += 1
            new = os.path.join(base, f)
            rel = os.path.relpath(new, MAIN)
            sh = os.path.join(SHELTER, 'app/src/main/java/net/typeblog/shelter', rel)
            if not os.path.exists(sh):
                continue
            a, b = norm(new), norm(sh)
            if not a or not b:
                continue
            shared = sum(1 for line in a if line in set(b))
            pct = shared * 100 // len(a)
            if pct >= THRESHOLD:
                derived_files += 1
                derived_lines += len(b)
                rows.append((pct, rel, len(b), len(a)))
    rows.sort(reverse=True)
    print(f'{"shelter%":<10}{"shelter lines":<15}{"our lines":<12}file')
    for pct, rel, shl, nwl in rows:
        print(f'{str(pct) + "%":<10}{shl:<15}{nwl:<12}{rel}')
    print()
    print(f'files total: {total_files}   still Shelter-derived: {derived_files}   '
          f'Shelter lines remaining: {derived_lines}')
    if derived_files == 0:
        print('CLEAN — no Shelter-derived file left. A separate repository is now possible.')
        return 0
    print('Still a GPLv3 derivative. Do not create a "clean" repository yet.')
    return 1


if __name__ == '__main__':
    sys.exit(main())
