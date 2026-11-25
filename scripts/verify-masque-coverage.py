#!/usr/bin/env python3
from pathlib import Path
import sys

import defusedxml.ElementTree as ET


def main() -> int:
    report = Path("engine-masque/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
    root = ET.parse(report).getroot()
    classes = {entry.attrib["name"] for entry in root.iter("class")}
    expected = {
        "MasqueEngine",
        "MasqueRuntime",
        "MasqueProfile",
        "MasqueProfile$Companion",
        "MasqueProfileStore",
        "MasqueTransport",
        "MasqueCommand",
        "TlsMasqueReadinessProbe",
        "ManagedMasqueProcess",
        "NativeMasqueProcessLauncher",
    }
    missing = expected - {name.rsplit("/", 1)[-1] for name in classes}
    if missing:
        print("Missing production classes: " + ", ".join(sorted(missing)), file=sys.stderr)
        return 1
    counters = {entry.attrib["type"]: entry.attrib for entry in root.findall("counter")}
    for kind in ("LINE", "BRANCH"):
        counter = counters.get(kind, {})
        covered = int(counter.get("covered", 0))
        missed = int(counter.get("missed", 0))
        if covered + missed == 0:
            print(f"Missing {kind} coverage", file=sys.stderr)
            return 1
        print(f"MASQUE {kind}: {covered}/{covered + missed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
