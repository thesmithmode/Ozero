import hashlib
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "release_mirrors", Path(__file__).with_name("release-mirrors.py")
)
mirrors = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mirrors)


class ReleaseMirrorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.tag = "v1.2.3"
        apk = self.directory / f"Ozero-{self.tag}.apk"
        apk.write_bytes(b"signed apk")
        checksum = self.directory / f"{apk.name}.sha256"
        checksum.write_text(f"{hashlib.sha256(apk.read_bytes()).hexdigest()}  {apk.name}\n")
        signature = self.directory / f"{apk.name}.asc"
        signature.write_text("signature")
        self.files = (apk, checksum, signature)

    def test_original_files(self):
        (self.directory / "unrelated").touch()
        self.assertEqual(mirrors.release_files(self.directory, self.tag), self.files)

    def test_missing_signature(self):
        self.files[2].unlink()
        with self.assertRaisesRegex(ValueError, "detached GPG"):
            mirrors.release_files(self.directory, self.tag)

    def test_wrong_checksum(self):
        self.files[0].write_bytes(b"corrupt")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            mirrors.release_files(self.directory, self.tag)

    def test_unsafe_tag(self):
        with self.assertRaisesRegex(ValueError, "Invalid release tag"):
            mirrors.release_files(self.directory, "../bad")

    @patch.object(mirrors, "run")
    def test_sourceforge_pinned_ssh_and_exact_files(self, run):
        env = dict(SOURCEFORGE_PROJECT="project", SOURCEFORGE_USERNAME="user",
                   SOURCEFORGE_SSH_KEY="key", SOURCEFORGE_KNOWN_HOSTS="hosts")
        with patch.dict(os.environ, env, clear=True):
            mirrors.publish_sourceforge(self.files, self.tag)
        command = run.call_args.args[0]
        self.assertIn("StrictHostKeyChecking=yes", command[command.index("-e") + 1])
        self.assertIn("--checksum", command)
        self.assertEqual(command[-4:-1], [str(file) for file in self.files])
        self.assertEqual(command[-1], "user@frs.sourceforge.net:/home/frs/project/project/v1.2.3/")

    @patch.object(mirrors, "run")
    def test_sourceforge_missing_key_fails_before_upload(self, run):
        with patch.dict(os.environ, {"SOURCEFORGE_PROJECT": "project", "SOURCEFORGE_USERNAME": "user"}, clear=True):
            with self.assertRaisesRegex(ValueError, "SOURCEFORGE_SSH_KEY"):
                mirrors.publish_sourceforge(self.files, self.tag)
        run.assert_not_called()

    @patch.object(mirrors, "run")
    def test_gitverse_rejects_conflicting_tag(self, run):
        run.side_effect = ["a" * 40 + "\n", "b" * 40 + "\trefs/tags/v1.2.3\n"]
        with self.assertRaisesRegex(RuntimeError, "different commit"):
            mirrors.sync_gitverse_tag("owner/project", self.tag, "user", "token")
        self.assertEqual(run.call_count, 2)

    @patch.object(mirrors, "run")
    def test_gitverse_pushes_only_release_tag(self, run):
        sha = "a" * 40
        run.side_effect = [sha + "\n", "", None]
        self.assertEqual(mirrors.sync_gitverse_tag("owner/project", self.tag, "user", "token"), sha)
        self.assertEqual(run.call_args.args[0][-3:], ["push", "https://gitverse.ru/owner/project.git", f"{sha}:refs/tags/{self.tag}"])

    def gitverse(self, existing=False, fail=False):
        calls = []

        def api(repository, token, method, path, expected, **kwargs):
            calls.append((method, path, kwargs))
            if method == "GET" and path.startswith("/tags/"):
                return (200, {"id": 1}) if existing else (404, None)
            if method == "POST" and path == "":
                return 201, {"id": 1}
            if method == "GET":
                assets = [{"id": 9, "name": self.files[0].name}, {"id": 10, "name": "other.zip"}]
                return 200, assets if existing else []
            if method == "POST":
                file = kwargs["attachment"]
                if fail:
                    raise RuntimeError("upload failed")
                return 201, {"name": file.name, "size": file.stat().st_size}
            return expected[0], None

        env = dict(GITVERSE_REPOSITORY="owner/project", GITVERSE_USERNAME="user",
                   GITVERSE_TOKEN="token", GITHUB_REPOSITORY="owner/project")
        with patch.dict(os.environ, env, clear=True), patch.object(
            mirrors, "sync_gitverse_tag", return_value="a" * 40
        ), patch.object(mirrors, "gitverse_api", side_effect=api):
            if fail:
                with self.assertRaisesRegex(RuntimeError, "upload failed"):
                    mirrors.publish_gitverse(self.files, self.tag)
            else:
                mirrors.publish_gitverse(self.files, self.tag)
        return calls

    def test_gitverse_draft_then_publish(self):
        calls = self.gitverse()
        self.assertTrue(calls[1][2]["payload"]["draft"])
        self.assertEqual([c[2]["attachment"] for c in calls if "attachment" in c[2]], list(self.files))
        self.assertFalse(calls[-1][2]["payload"]["draft"])

    def test_gitverse_rerun_replaces_only_managed_assets(self):
        calls = self.gitverse(existing=True)
        self.assertTrue(calls[1][2]["payload"]["draft"])
        self.assertEqual([c[1] for c in calls if c[0] == "DELETE"], ["/1/assets/9"])
        self.assertFalse(calls[-1][2]["payload"]["draft"])

    def test_gitverse_failed_upload_stays_draft(self):
        calls = self.gitverse(fail=True)
        self.assertFalse(any(c[0] == "PATCH" for c in calls))


if __name__ == "__main__":
    unittest.main()
