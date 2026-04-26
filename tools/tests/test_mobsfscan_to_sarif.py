"""
Тесты для mobsfscan-to-sarif.py
Запуск: pytest tools/tests/test_mobsfscan_to_sarif.py -v
"""
import json
import sys
import tempfile
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent))

from mobsfscan_to_sarif import (
    SARIF_VERSION,
    convert,
    finding_to_result,
    finding_to_rule,
    parse_mobsfscan,
)


SAMPLE_MOBSFSCAN_OLD_FORMAT = {
    "results": {
        "android_logging": {
            "files": [
                {
                    "file_path": "/code/app/src/main/java/ru/ozero/Foo.kt",
                    "match_lines": [42, 42],
                    "match_string": "Log.d(TAG, \"secret\")",
                    "match_position": [5, 37],
                }
            ],
            "metadata": {
                "cwe": "CWE-532",
                "description": "Log statements with dynamic data may leak sensitive info",
                "owasp-mobile": "M1: Improper Platform Usage",
                "severity": "WARNING",
            },
        }
    }
}

SAMPLE_MOBSFSCAN_NEW_FORMAT = {
    "results": [
        {
            "rule_id": "android_logging",
            "title": "Log Statement Vulnerability",
            "description": "Logging sensitive information",
            "severity": "HIGH",
            "owasp-mobile": "M1",
            "cwe": "CWE-532",
            "files": [
                {
                    "file_path": "/code/app/src/main/java/Foo.kt",
                    "match_lines": [10],
                }
            ],
        }
    ]
}


class TestParseMobsfscan:
    def test_old_format_dict(self):
        findings = parse_mobsfscan(SAMPLE_MOBSFSCAN_OLD_FORMAT)
        assert len(findings) == 1
        f = findings[0]
        assert f["rule_id"] == "android_logging"
        assert f["severity"] == "WARNING"
        assert len(f["files"]) == 1

    def test_new_format_list(self):
        findings = parse_mobsfscan(SAMPLE_MOBSFSCAN_NEW_FORMAT)
        assert len(findings) == 1
        f = findings[0]
        assert f["rule_id"] == "android_logging"
        assert f["severity"] == "HIGH"

    def test_empty_results(self):
        findings = parse_mobsfscan({"results": {}})
        assert findings == []

    def test_empty_list(self):
        findings = parse_mobsfscan({"results": []})
        assert findings == []


class TestFindingToResult:
    def test_result_structure(self):
        finding = {
            "rule_id": "test_rule",
            "title": "Test",
            "description": "Test description",
            "severity": "HIGH",
            "files": [{"file_path": "/code/Foo.kt", "match_lines": [5]}],
        }
        result = finding_to_result(finding)
        assert result["ruleId"] == "test_rule"
        assert result["level"] == "error"
        assert "locations" in result
        assert result["locations"][0]["physicalLocation"]["region"]["startLine"] == 5

    def test_warning_severity(self):
        finding = {
            "rule_id": "r",
            "title": "T",
            "description": "D",
            "severity": "WARNING",
            "files": [],
        }
        result = finding_to_result(finding)
        assert result["level"] == "warning"
        assert "locations" not in result

    def test_path_stripped_of_leading_slash(self):
        finding = {
            "rule_id": "r",
            "title": "T",
            "description": "D",
            "severity": "INFO",
            "files": [{"file_path": "/code/src/Foo.kt", "match_lines": [1]}],
        }
        result = finding_to_result(finding)
        uri = result["locations"][0]["physicalLocation"]["artifactLocation"]["uri"]
        assert not uri.startswith("/")

    def test_line_number_minimum_is_1(self):
        finding = {
            "rule_id": "r",
            "title": "T",
            "description": "D",
            "severity": "INFO",
            "files": [{"file_path": "Foo.kt", "match_lines": [0]}],
        }
        result = finding_to_result(finding)
        assert result["locations"][0]["physicalLocation"]["region"]["startLine"] == 1


class TestFindingToRule:
    def test_rule_has_required_fields(self):
        finding = {
            "rule_id": "test_rule",
            "title": "Test Title",
            "description": "Test description",
            "severity": "HIGH",
            "owasp": "M1",
            "cwe": "CWE-532",
            "files": [],
        }
        rule = finding_to_rule(finding)
        assert rule["id"] == "test_rule"
        assert rule["shortDescription"]["text"] == "Test Title"
        assert "M1" in rule["properties"]["tags"]
        assert "CWE-532" in rule["properties"]["tags"]


class TestConvert:
    def test_output_is_valid_sarif(self, tmp_path):
        input_file = tmp_path / "mobsf.json"
        output_file = tmp_path / "output.sarif"
        input_file.write_text(json.dumps(SAMPLE_MOBSFSCAN_OLD_FORMAT))

        count = convert(str(input_file), str(output_file))

        assert count == 1
        with output_file.open() as f:
            sarif = json.load(f)
        assert sarif["version"] == SARIF_VERSION
        assert len(sarif["runs"]) == 1
        assert len(sarif["runs"][0]["results"]) == 1

    def test_convert_new_format(self, tmp_path):
        input_file = tmp_path / "mobsf.json"
        output_file = tmp_path / "output.sarif"
        input_file.write_text(json.dumps(SAMPLE_MOBSFSCAN_NEW_FORMAT))

        count = convert(str(input_file), str(output_file))

        assert count == 1
        with output_file.open() as f:
            sarif = json.load(f)
        rules = sarif["runs"][0]["tool"]["driver"]["rules"]
        assert len(rules) == 1
        assert rules[0]["id"] == "android_logging"

    def test_convert_empty(self, tmp_path):
        input_file = tmp_path / "mobsf.json"
        output_file = tmp_path / "output.sarif"
        input_file.write_text(json.dumps({"results": {}}))

        count = convert(str(input_file), str(output_file))

        assert count == 0
        with output_file.open() as f:
            sarif = json.load(f)
        assert sarif["runs"][0]["results"] == []
