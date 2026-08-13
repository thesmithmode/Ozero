import re
import unittest
from pathlib import Path


class GradleCacheOwnershipTest(unittest.TestCase):
    def test_setup_gradle_is_the_only_gradle_cache_owner(self):
        conflicting_workflows = []
        for workflow in Path(".github/workflows").glob("*.yml"):
            source = workflow.read_text(encoding="utf-8")
            uses_setup_gradle = "gradle/actions/setup-gradle@" in source
            setup_java_owns_cache = re.search(
                r"(?m)^\s+cache:\s*gradle\s*$",
                source,
            )
            if uses_setup_gradle and setup_java_owns_cache:
                conflicting_workflows.append(workflow.as_posix())

        self.assertEqual(
            [],
            conflicting_workflows,
            "setup-java cache prevents setup-gradle from restoring and saving wrapper distributions",
        )

    def test_style_gate_primes_shared_native_binary_cache_before_fanout(self):
        source = Path(".github/workflows/ci.yml").read_text(encoding="utf-8")
        style_job = source.split("  kotlin-style:", 1)[1].split("\n  test-singbox:", 1)[0]

        self.assertIn(":engine-byedpi:downloadBinaries", style_job)
        self.assertIn(":engine-masterdns:downloadBinaries", style_job)
