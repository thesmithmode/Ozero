import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def required_env(name):
    value = os.environ.get(name, "")
    if not value.strip():
        raise ValueError(f"Missing configuration: {name}")
    return value


def validate(value, pattern, label):
    if not re.fullmatch(pattern, value):
        raise ValueError(f"Invalid {label}")
    return value


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def release_files(directory, tag):
    validate(tag, r"v[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?", "release tag")
    apk = directory / f"Ozero-{tag}.apk"
    checksum = directory / f"{apk.name}.sha256"
    signature = directory / f"{apk.name}.asc"
    files = (apk, checksum, signature)
    if any(not p.is_file() or p.is_symlink() or p.stat().st_size == 0 for p in files):
        raise ValueError("Mirrors require the original APK, SHA-256 and detached GPG signature")
    if checksum.read_text(encoding="ascii").splitlines() != [f"{digest(apk)}  {apk.name}"]:
        raise ValueError("APK checksum mismatch")
    return files


def run(command, *, env=None, data=None, capture=False):
    result = subprocess.run(
        command, env=env, input=data, text=True, stdout=subprocess.PIPE if capture else None,
        check=False, timeout=900,
    )
    if result.returncode:
        raise RuntimeError(f"{command[0]} failed (exit {result.returncode})")
    return result.stdout if capture else None


def gitverse_api(repository, token, method, path, expected, *, payload=None, attachment=None):
    with tempfile.TemporaryDirectory() as temporary:
        response = Path(temporary) / "response"
        command = [
            "curl", "--silent", "--show-error", "--connect-timeout", "20", "--max-time", "600",
            "--request", method, "--header", "@-", "--header",
            "Accept: application/vnd.gitverse.object+json;version=1",
            "--output", str(response), "--write-out", "%{http_code}",
        ]
        if payload is not None:
            command += ["--header", "Content-Type: application/json", "--data-binary", json.dumps(payload)]
        if attachment is not None:
            command += ["--form", f"attachment=@{attachment}"]
        command.append(f"https://api.gitverse.ru/repos/{repository}/releases{path}")
        status = int(run(command, data=f"Authorization: Bearer {token}\n", capture=True))
        if status not in expected:
            raise RuntimeError(f"GitVerse {method} failed (HTTP {status})")
        return status, json.loads(response.read_text()) if status not in (204, 404) else None


def sync_gitverse_tag(repository, tag, username, token):
    remote = f"https://gitverse.ru/{repository}.git"
    ref = f"refs/tags/{tag}"
    sha = run(["git", "rev-parse", "HEAD"], capture=True).strip()
    with tempfile.TemporaryDirectory() as temporary:
        askpass = Path(temporary) / "askpass"
        askpass.write_text('#!/bin/sh\ncase "$1" in\n  *Username*) printf "%s\\n" "$GITVERSE_USERNAME";;\n  *) printf "%s\\n" "$GITVERSE_TOKEN";;\nesac\n')
        askpass.chmod(0o700)
        env = dict(os.environ, GIT_ASKPASS=str(askpass), GIT_TERMINAL_PROMPT="0",
                   GITVERSE_USERNAME=username, GITVERSE_TOKEN=token, LC_ALL="C")
        git = ["git", "-c", "credential.helper=", "-c", "http.followRedirects=false"]
        refs = run(git + ["ls-remote", remote, ref, ref + "^{}"], env=env, capture=True)
        found = dict(line.split("\t", 1)[::-1] for line in refs.splitlines())
        if found:
            if found.get(ref + "^{}", found.get(ref)) != sha:
                raise RuntimeError("GitVerse tag points to a different commit")
        else:
            run(git + ["push", remote, f"{sha}:{ref}"], env=env)
    return sha


def publish_gitverse(files, tag):
    repository = validate(required_env("GITVERSE_REPOSITORY"),
                          r"[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+", "GitVerse repository")
    username = required_env("GITVERSE_USERNAME")
    token = required_env("GITVERSE_TOKEN")
    sha = sync_gitverse_tag(repository, tag, username, token)
    source = validate(required_env("GITHUB_REPOSITORY"), r"[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+", "GitHub repository")

    def api(method, path, expected, **kwargs):
        return gitverse_api(repository, token, method, path, expected, **kwargs)

    metadata = {
        "name": tag,
        "body": f"Original release: https://github.com/{source}/releases/tag/{tag}",
        "draft": True,
        "prerelease": "-" in tag,
        "is_authorized_only": False,
    }
    status, release = api("GET", f"/tags/{tag}", (200, 404))
    if status == 404:
        _, release = api("POST", "", (201,), payload=dict(metadata, tag_name=tag, target_commitish=sha))
    release_id = int(release["id"])
    if status == 200:
        api("PATCH", f"/{release_id}", (200,), payload=metadata)
    path = f"/{release_id}/assets"
    names = {file.name for file in files}
    page = 1
    existing = []
    while True:
        _, assets = api("GET", f"{path}?page={page}&per_page=50", (200,))
        existing.extend(assets)
        if len(assets) < 50:
            break
        page += 1
    for asset in existing:
        if asset["name"] in names:
            api("DELETE", f"{path}/{int(asset['id'])}", (204,))
    for file in files:
        _, asset = api("POST", f"{path}?name={file.name}", (201,), attachment=file)
        if asset["name"] != file.name or int(asset["size"]) != file.stat().st_size:
            raise RuntimeError("GitVerse asset metadata mismatch")
    api("PATCH", f"/{release_id}", (200,), payload=dict(metadata, draft=False))


def publish_sourceforge(files, tag):
    project = validate(required_env("SOURCEFORGE_PROJECT"), r"[A-Za-z0-9_-]+", "SourceForge project")
    username = validate(required_env("SOURCEFORGE_USERNAME"), r"[A-Za-z0-9_-]+", "SourceForge username")
    with tempfile.TemporaryDirectory() as temporary:
        key = Path(temporary) / "key"
        hosts = Path(temporary) / "known_hosts"
        key.write_text(required_env("SOURCEFORGE_SSH_KEY").rstrip() + "\n")
        key.chmod(0o600)
        hosts.write_text(required_env("SOURCEFORGE_KNOWN_HOSTS").rstrip() + "\n")
        ssh = (
            f"ssh -i {key} -o IdentitiesOnly=yes -o BatchMode=yes "
            f"-o StrictHostKeyChecking=yes -o UserKnownHostsFile={hosts} -o GlobalKnownHostsFile=/dev/null "
            "-o ConnectTimeout=20"
        )
        destination = f"{username}@frs.sourceforge.net:/home/frs/project/{project}/{tag}/"
        run(["rsync", "--checksum", "--partial-dir=.rsync-partial", "--timeout=120",
             "-e", ssh, *(str(file) for file in files), destination])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("provider", choices=("gitverse", "sourceforge"))
    parser.add_argument("tag")
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    try:
        files = release_files(args.directory, args.tag)
        run(["gpg", "--batch", "--verify", str(files[2]), str(files[0])])
        publishers = {"gitverse": publish_gitverse, "sourceforge": publish_sourceforge}
        publishers[args.provider](files, args.tag)
    except (ValueError, RuntimeError, OSError, KeyError, subprocess.TimeoutExpired) as error:
        message = str(error) if isinstance(error, (ValueError, RuntimeError)) else type(error).__name__
        parser.exit(1, f"Mirror publication failed: {message}\n")
    print(f"Published {args.tag} to {args.provider}")


if __name__ == "__main__":
    main()
