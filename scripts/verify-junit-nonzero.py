#!/usr/bin/env python3
import glob
import os
import sys
import defusedxml.ElementTree as ET


def count_testcases(module_path: str) -> int:
    pattern = os.path.join(module_path, "build", "test-results", "**", "*.xml")
    total = 0
    for report in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        if root.tag == "testsuite":
            total += len(root.findall("testcase"))
        elif root.tag == "testsuites":
            total += sum(len(suite.findall("testcase")) for suite in root.findall("testsuite"))
    return total


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: verify-junit-nonzero.py <module-dir> [...]", file=sys.stderr)
        return 2
    failed = []
    for module in sys.argv[1:]:
        count = count_testcases(module)
        print(f"{module}: {count} testcases")
        if count <= 0:
            failed.append(module)
    if failed:
        print("modules with zero discovered tests: " + ", ".join(failed), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
