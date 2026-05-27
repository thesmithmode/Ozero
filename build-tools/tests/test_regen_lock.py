"""Tests for build-tools/regen_lock.py — multi-engine lock merge."""
import io
import os
import sys
import textwrap
import unittest
from pathlib import Path
from unittest import mock

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import regen_lock as rl  # noqa: E402


class ParseManifestTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(self._tmp())

    def _tmp(self):
        import tempfile
        d = tempfile.mkdtemp()
        self.addCleanup(__import__("shutil").rmtree, d)
        return d

    def test_byedpi_manifest_4_abi(self):
        manifest = self.tmp / "manifest.txt"
        manifest.write_text(textwrap.dedent("""\
            # build_byedpi manifest
            source_repo=https://github.com/hufrea/byedpi
            source_commit=abc123
            ndk=android-ndk-r27c
            api_level=24

            # SHA256:
            aaaa000000000000000000000000000000000000000000000000000000000000  libbyedpi-arm64-v8a.so
            bbbb000000000000000000000000000000000000000000000000000000000000  libbyedpi-armeabi-v7a.so
            cccc000000000000000000000000000000000000000000000000000000000000  libbyedpi-x86_64.so
            dddd000000000000000000000000000000000000000000000000000000000000  libbyedpi-x86.so
        """))
        meta, arts = rl.parse_manifest(str(manifest))
        self.assertEqual(meta["source_repo"], "https://github.com/hufrea/byedpi")
        self.assertEqual(meta["source_commit"], "abc123")
        self.assertEqual(len(arts), 4)
        names = sorted(a[0] for a in arts)
        self.assertEqual(names[0], "libbyedpi-arm64-v8a.so")

    def test_xray_manifest_single_aar(self):
        manifest = self.tmp / "manifest.txt"
        manifest.write_text(textwrap.dedent("""\
            # build_xray manifest
            source_repo=https://github.com/XTLS/Xray-core
            source_commit=def456
            xray_version=v25.10.1

            # SHA256:
            eeee000000000000000000000000000000000000000000000000000000000000  libxray.aar
        """))
        meta, arts = rl.parse_manifest(str(manifest))
        self.assertEqual(arts, [("libxray.aar", "eeee000000000000000000000000000000000000000000000000000000000000")])


class BuildArtifactEntriesTest(unittest.TestCase):
    def setUp(self):
        import tempfile
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(__import__("shutil").rmtree, str(self.tmp))

    def _touch(self, name, size=100):
        f = self.tmp / name
        f.write_bytes(b"x" * size)
        return f

    def test_byedpi_entries_have_abi_and_jniLibs(self):
        self._touch("libbyedpi-arm64-v8a.so", 1024)
        self._touch("libbyedpi-x86_64.so", 2048)
        manifest = self.tmp / "manifest.txt"
        manifest.write_text("source_repo=r\nsource_commit=c\n\n# SHA256:\n"
                            "aaaa000000000000000000000000000000000000000000000000000000000000  libbyedpi-arm64-v8a.so\n"
                            "bbbb000000000000000000000000000000000000000000000000000000000000  libbyedpi-x86_64.so\n")
        meta, arts = rl.parse_manifest(str(manifest))
        entries = rl.build_artifact_entries(
            engine="byedpi", tag="byedpi-deadbeef", repo="owner/Ozero",
            manifest_path=str(manifest), meta=meta, artifacts=arts,
        )
        self.assertEqual(len(entries), 2)
        e = next(x for x in entries if x["name"] == "libbyedpi-arm64-v8a.so")
        self.assertEqual(e["engine"], "byedpi")
        self.assertEqual(e["abi"], "arm64-v8a")
        self.assertEqual(e["destination"], "jniLibs")
        self.assertEqual(e["sha256"], "aaaa" + "0" * 60)
        self.assertEqual(e["size_bytes"], 1024)
        self.assertEqual(e["source_commit"], "c")
        self.assertIn("owner/Ozero/releases/download/byedpi-deadbeef/libbyedpi-arm64-v8a.so",
                      e["download_url"])

    def test_xray_aar_entry_has_no_abi_and_libs_destination(self):
        self._touch("libxray.aar", 4096)
        manifest = self.tmp / "manifest.txt"
        manifest.write_text("source_repo=r\nsource_commit=c\n\n# SHA256:\n"
                            "eeee000000000000000000000000000000000000000000000000000000000000  libxray.aar\n")
        meta, arts = rl.parse_manifest(str(manifest))
        entries = rl.build_artifact_entries(
            engine="xray", tag="xray-cafebabe", repo="owner/Ozero",
            manifest_path=str(manifest), meta=meta, artifacts=arts,
        )
        self.assertEqual(len(entries), 1)
        e = entries[0]
        self.assertEqual(e["name"], "libxray.aar")
        self.assertEqual(e["engine"], "xray")
        self.assertNotIn("abi", e)
        self.assertEqual(e["destination"], "libs")
        self.assertEqual(e["size_bytes"], 4096)

    def test_unknown_engine_rejected(self):
        with self.assertRaises(KeyError):
            rl.build_artifact_entries(
                engine="bogus", tag="x", repo="r", manifest_path="/dev/null",
                meta={}, artifacts=[],
            )

    def test_skips_artifacts_not_matching_engine_pattern(self):
        manifest = self.tmp / "manifest.txt"
        # File for engine byedpi BUT one entry doesn't match pattern
        self._touch("libbyedpi-arm64-v8a.so", 100)
        # Don't create the unmatched file — но parse_manifest sets in_sha after #SHA256.
        manifest.write_text("source_repo=r\nsource_commit=c\n\n# SHA256:\n"
                            "aaaa000000000000000000000000000000000000000000000000000000000000  libbyedpi-arm64-v8a.so\n"
                            "0000000000000000000000000000000000000000000000000000000000000000  randomfile.txt\n")
        meta, arts = rl.parse_manifest(str(manifest))
        entries = rl.build_artifact_entries(
            engine="byedpi", tag="t", repo="r",
            manifest_path=str(manifest), meta=meta, artifacts=arts,
        )
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["name"], "libbyedpi-arm64-v8a.so")


class MergeAndWriteTest(unittest.TestCase):
    def setUp(self):
        import tempfile
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(__import__("shutil").rmtree, str(self.tmp))

    def _write_lock(self, content):
        p = self.tmp / "binaries.lock.yaml"
        p.write_text(content)
        return p

    def _read_lock(self, p):
        return yaml.safe_load(Path(p).read_text())

    def test_load_existing_returns_empty_when_missing(self):
        p = self.tmp / "missing.yaml"
        existing = rl.load_existing_lock(str(p))
        self.assertEqual(existing["artifacts"], [])

    def test_load_existing_parses_real_lock(self):
        p = self._write_lock(textwrap.dedent("""\
            tag: byedpi-aaaa1111
            generated_at: 2026-04-25T00:00:00Z
            artifacts:
              - name: libbyedpi-arm64-v8a.so
                engine: byedpi
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://example.com/x.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 100
                source_repo: r
                source_commit: c
        """))
        existing = rl.load_existing_lock(str(p))
        self.assertEqual(existing["tag"], "byedpi-aaaa1111")
        self.assertEqual(len(existing["artifacts"]), 1)

    def test_merge_replaces_only_target_engine_artifacts(self):
        existing = {
            "tag": "byedpi-aaaa1111",
            "generated_at": "old",
            "artifacts": [
                {"name": "libbyedpi-arm64-v8a.so", "engine": "byedpi", "abi": "arm64-v8a",
                 "destination": "jniLibs", "download_url": "old-url", "sha256": "a" * 64,
                 "size_bytes": 1, "source_repo": "r", "source_commit": "c"},
                {"name": "libxray.aar", "engine": "xray", "destination": "libs",
                 "download_url": "old-xray-url", "sha256": "b" * 64,
                 "size_bytes": 999, "source_repo": "r", "source_commit": "old-x"},
            ],
        }
        new_xray = [{
            "name": "libxray.aar", "engine": "xray", "destination": "libs",
            "download_url": "new-xray-url", "sha256": "c" * 64,
            "size_bytes": 8888, "source_repo": "r", "source_commit": "new-x",
        }]
        merged = rl.merge_engine_artifacts(existing, "xray", new_xray, "xray-newtag")
        self.assertEqual(merged["tag"], "xray-newtag")
        names = sorted(a["name"] for a in merged["artifacts"])
        self.assertEqual(names, ["libbyedpi-arm64-v8a.so", "libxray.aar"])
        # byedpi unchanged
        b = next(a for a in merged["artifacts"] if a["engine"] == "byedpi")
        self.assertEqual(b["sha256"], "a" * 64)
        self.assertEqual(b["download_url"], "old-url")
        # xray replaced
        x = next(a for a in merged["artifacts"] if a["engine"] == "xray")
        self.assertEqual(x["sha256"], "c" * 64)
        self.assertEqual(x["download_url"], "new-xray-url")
        self.assertEqual(x["source_commit"], "new-x")

    def test_merge_first_run_creates_artifacts_list(self):
        existing = {"tag": "", "generated_at": "", "artifacts": []}
        new = [{"name": "libxray.aar", "engine": "xray", "destination": "libs",
                "download_url": "u", "sha256": "d" * 64, "size_bytes": 1,
                "source_repo": "r", "source_commit": "c"}]
        merged = rl.merge_engine_artifacts(existing, "xray", new, "xray-tag")
        self.assertEqual(len(merged["artifacts"]), 1)
        self.assertEqual(merged["tag"], "xray-tag")

    def test_write_lock_roundtrips_through_yaml(self):
        lock = {
            "tag": "xray-deadbeef",
            "generated_at": "2026-04-25T12:00:00Z",
            "artifacts": [
                {"name": "libxray.aar", "engine": "xray", "destination": "libs",
                 "download_url": "https://example.com/libxray.aar",
                 "sha256": "f" * 64, "size_bytes": 15728640,
                 "source_repo": "https://github.com/XTLS/Xray-core",
                 "source_commit": "deadbeefcafebabe"},
                {"name": "libbyedpi-arm64-v8a.so", "engine": "byedpi", "abi": "arm64-v8a",
                 "destination": "jniLibs", "download_url": "https://example.com/x.so",
                 "sha256": "a" * 64, "size_bytes": 100,
                 "source_repo": "https://github.com/hufrea/byedpi",
                 "source_commit": "abc"},
            ],
        }
        out = self.tmp / "binaries.lock.yaml"
        rl.write_lock(str(out), lock)
        loaded = yaml.safe_load(out.read_text())
        self.assertEqual(loaded["tag"], "xray-deadbeef")
        self.assertEqual(len(loaded["artifacts"]), 2)
        x = next(a for a in loaded["artifacts"] if a["engine"] == "xray")
        self.assertEqual(x["destination"], "libs")
        self.assertNotIn("abi", x)
        b = next(a for a in loaded["artifacts"] if a["engine"] == "byedpi")
        self.assertEqual(b["abi"], "arm64-v8a")

    def test_write_lock_canonical_key_order(self):
        """Output should have name as first key, then engine, abi (if present), destination, ..."""
        lock = {
            "tag": "t", "generated_at": "g",
            "artifacts": [{"source_commit": "c", "name": "z.so", "engine": "byedpi",
                           "abi": "arm64-v8a", "destination": "jniLibs",
                           "download_url": "u", "sha256": "a" * 64, "size_bytes": 1,
                           "source_repo": "r"}],
        }
        out = self.tmp / "out.yaml"
        rl.write_lock(str(out), lock)
        content = out.read_text()
        # name appears before engine which appears before sha256 etc.
        lines = content.splitlines()
        name_idx = next(i for i, l in enumerate(lines) if "name:" in l and "z.so" in l)
        engine_idx = next(i for i, l in enumerate(lines) if "engine:" in l and "byedpi" in l)
        sha_idx = next(i for i, l in enumerate(lines) if "sha256:" in l)
        self.assertLess(name_idx, engine_idx)
        self.assertLess(engine_idx, sha_idx)


class MainCliTest(unittest.TestCase):
    def setUp(self):
        import tempfile
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(__import__("shutil").rmtree, str(self.tmp))

    def test_main_xray_first_run(self):
        # Prepare manifest + libxray.aar binary
        (self.tmp / "libxray.aar").write_bytes(b"xray-aar-bytes" * 100)
        manifest = self.tmp / "manifest.txt"
        manifest.write_text("source_repo=https://github.com/XTLS/Xray-core\n"
                            "source_commit=cafebabe\n\n# SHA256:\n"
                            "f000000000000000000000000000000000000000000000000000000000000000  libxray.aar\n")
        out = self.tmp / "binaries.lock.yaml"
        with mock.patch.object(sys, "argv", [
            "regen_lock.py",
            "--engine", "xray",
            "--tag", "xray-12345678",
            "--repo", "owner/Ozero",
            "--manifest", str(manifest),
            "--out", str(out),
        ]):
            rl.main()
        loaded = yaml.safe_load(out.read_text())
        self.assertEqual(loaded["tag"], "xray-12345678")
        self.assertEqual(len(loaded["artifacts"]), 1)
        a = loaded["artifacts"][0]
        self.assertEqual(a["engine"], "xray")
        self.assertEqual(a["destination"], "libs")
        self.assertNotIn("abi", a)
        self.assertEqual(a["download_url"],
                         "https://github.com/owner/Ozero/releases/download/xray-12345678/libxray.aar")

    def test_main_xray_added_to_existing_byedpi(self):
        # Existing lock with byedpi
        existing_text = textwrap.dedent("""\
            tag: byedpi-aaaa1111
            generated_at: 2026-04-25T00:00:00Z
            artifacts:
              - name: libbyedpi-arm64-v8a.so
                engine: byedpi
                abi: arm64-v8a
                destination: jniLibs
                download_url: https://github.com/x/y/releases/download/byedpi-aaaa1111/libbyedpi-arm64-v8a.so
                sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                size_bytes: 100
                source_repo: https://github.com/hufrea/byedpi
                source_commit: byedpi-commit
        """)
        out = self.tmp / "binaries.lock.yaml"
        out.write_text(existing_text)

        (self.tmp / "libxray.aar").write_bytes(b"x" * 50)
        manifest = self.tmp / "manifest.txt"
        manifest.write_text("source_repo=https://github.com/XTLS/Xray-core\n"
                            "source_commit=xray-commit\n\n# SHA256:\n"
                            "f000000000000000000000000000000000000000000000000000000000000000  libxray.aar\n")
        with mock.patch.object(sys, "argv", [
            "regen_lock.py",
            "--engine", "xray",
            "--tag", "xray-87654321",
            "--repo", "owner/Ozero",
            "--manifest", str(manifest),
            "--out", str(out),
        ]):
            rl.main()
        loaded = yaml.safe_load(out.read_text())
        self.assertEqual(loaded["tag"], "xray-87654321")
        self.assertEqual(len(loaded["artifacts"]), 2)
        engines = sorted(a["engine"] for a in loaded["artifacts"])
        self.assertEqual(engines, ["byedpi", "xray"])
        # byedpi entry intact (sha unchanged)
        b = next(a for a in loaded["artifacts"] if a["engine"] == "byedpi")
        self.assertEqual(b["sha256"], "a" * 64)
        self.assertEqual(b["source_commit"], "byedpi-commit")


if __name__ == "__main__":
    unittest.main()
