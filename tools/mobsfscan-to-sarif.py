#!/usr/bin/env python3
"""
tools/mobsfscan-to-sarif.py
Конвертирует mobsfscan JSON output в SARIF 2.1.0 для upload в GitHub Security tab.

Использование:
    python3 tools/mobsfscan-to-sarif.py mobsf.json sarif-output.sarif
"""

import json
import sys
import argparse
from pathlib import Path
from datetime import datetime, timezone


SARIF_SCHEMA = "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"
SARIF_VERSION = "2.1.0"
TOOL_NAME = "mobsfscan"
TOOL_VERSION = "1.0.0"
TOOL_URI = "https://github.com/MobSF/mobsfscan"

# Маппинг severity mobsfscan → SARIF level
SEVERITY_MAP = {
    "ERROR": "error",
    "WARNING": "warning",
    "INFO": "note",
    "HIGH": "error",
    "MEDIUM": "warning",
    "LOW": "note",
}


def parse_mobsfscan(data: dict) -> list[dict]:
    """
    Парсит mobsfscan JSON и возвращает список normalized findings.
    Поддерживает оба формата: results (dict) и results (list).
    """
    findings = []
    results = data.get("results", {})

    if isinstance(results, list):
        # Новый формат: список findings
        for item in results:
            findings.append({
                "rule_id": item.get("rule_id") or item.get("id", "unknown"),
                "title": item.get("title") or item.get("rule_id", ""),
                "description": item.get("description", ""),
                "severity": item.get("severity", "INFO").upper(),
                "files": item.get("files", []),
                "owasp": item.get("owasp-mobile", ""),
                "cwe": item.get("cwe", ""),
            })
    elif isinstance(results, dict):
        # Старый формат: dict rule_id → finding_data
        for rule_id, finding in results.items():
            metadata = finding.get("metadata", {})
            files = finding.get("files", [])
            findings.append({
                "rule_id": rule_id,
                "title": metadata.get("description", rule_id),
                "description": metadata.get("description", ""),
                "severity": metadata.get("severity", "INFO").upper(),
                "files": files,
                "owasp": metadata.get("owasp-mobile", ""),
                "cwe": metadata.get("cwe", ""),
            })

    return findings


def finding_to_result(finding: dict) -> dict:
    """Конвертирует normalized finding в SARIF result object."""
    level = SEVERITY_MAP.get(finding["severity"], "warning")
    rule_id = finding["rule_id"]

    locations = []
    for file_info in finding.get("files", []):
        file_path = file_info.get("file_path", "")
        line = file_info.get("match_lines", [1])
        line_num = line[0] if isinstance(line, list) and line else 1

        locations.append({
            "physicalLocation": {
                "artifactLocation": {
                    "uri": file_path.lstrip("/"),
                    "uriBaseId": "%SRCROOT%",
                },
                "region": {
                    "startLine": max(1, int(line_num)),
                },
            }
        })

    result: dict = {
        "ruleId": rule_id,
        "level": level,
        "message": {
            "text": finding.get("description") or finding.get("title", rule_id),
        },
    }

    if locations:
        result["locations"] = locations

    return result


def finding_to_rule(finding: dict) -> dict:
    """Конвертирует normalized finding в SARIF rule descriptor."""
    rule_id = finding["rule_id"]
    tags = []
    if finding.get("owasp"):
        tags.append(finding["owasp"])
    if finding.get("cwe"):
        tags.append(finding["cwe"])

    return {
        "id": rule_id,
        "name": rule_id,
        "shortDescription": {
            "text": finding.get("title") or rule_id,
        },
        "fullDescription": {
            "text": finding.get("description") or finding.get("title") or rule_id,
        },
        "properties": {
            "tags": tags,
            "severity": finding.get("severity", "INFO"),
        },
    }


def convert(input_path: str, output_path: str) -> int:
    """Конвертирует mobsfscan JSON в SARIF. Возвращает количество findings."""
    with open(input_path) as f:
        data = json.load(f)

    findings = parse_mobsfscan(data)

    # Дедупликация правил по rule_id
    rules_by_id: dict[str, dict] = {}
    for f in findings:
        rid = f["rule_id"]
        if rid not in rules_by_id:
            rules_by_id[rid] = finding_to_rule(f)

    results = [finding_to_result(f) for f in findings]

    sarif = {
        "$schema": SARIF_SCHEMA,
        "version": SARIF_VERSION,
        "runs": [
            {
                "tool": {
                    "driver": {
                        "name": TOOL_NAME,
                        "version": TOOL_VERSION,
                        "informationUri": TOOL_URI,
                        "rules": list(rules_by_id.values()),
                    }
                },
                "results": results,
                "invocations": [
                    {
                        "executionSuccessful": True,
                        "endTimeUtc": datetime.now(timezone.utc).isoformat(),
                    }
                ],
            }
        ],
    }

    with open(output_path, "w") as f:
        json.dump(sarif, f, indent=2, ensure_ascii=False)

    return len(findings)


def main():
    parser = argparse.ArgumentParser(description="Конвертирует mobsfscan JSON в SARIF 2.1.0")
    parser.add_argument("input", help="Входной файл mobsfscan JSON")
    parser.add_argument("output", help="Выходной файл SARIF")
    args = parser.parse_args()

    if not Path(args.input).exists():
        print(f"[mobsfscan-to-sarif] Файл не найден: {args.input}", file=sys.stderr)
        sys.exit(1)

    count = convert(args.input, args.output)
    print(f"[mobsfscan-to-sarif] Конвертировано {count} findings → {args.output}")


if __name__ == "__main__":
    main()
