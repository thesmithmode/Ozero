#!/usr/bin/env python3
"""Check upstream repos for new releases vs versions pinned в build_*.sh.

Output:
  - build-tools/upstream-bumps.md — markdown отчёт со списком найденных bump-ов
    (формат потребляется PR description'ом).
  - переменная окружения HAS_BUMPS=true|false пишется в $GITHUB_ENV если задан.

Usage (CI):
  python3 build-tools/check_upstream.py

Auth:
  Использует GH_TOKEN или GITHUB_TOKEN из окружения для повышения rate limit.
  Без токена — 60 req/h, что для нас (~6 запросов раз в неделю) хватает.
"""
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# (engine, build_script, version_var, upstream_repo, tag_strip_prefix)
TARGETS = [
    ("xray",       "build_xray.sh",       "XRAY_VERSION",       "2dust/AndroidLibXrayLite", ""),
    ("hysteria2",  "build_hysteria2.sh",  "HY2_VERSION",        "apernet/hysteria",          ""),
    ("amneziawg",  "build_amneziawg.sh",  "AWG_VERSION",        "amnezia-vpn/amneziawg-go",      ""),
    ("naive",      "build_naive.sh",      "NAIVE_VERSION",      "klzgrad/naiveproxy",        ""),
    ("byedpi",     None,                  None,                 "hufrea/byedpi",             "v"),
]


def read_pinned(script_name: str, var: str) -> str | None:
    path = ROOT / script_name
    if not path.exists():
        return None
    pat = re.compile(rf'^{var}="?\$\{{{var}:-([^}}\s"]+)\}}"?', re.M)
    m = pat.search(path.read_text())
    return m.group(1) if m else None


def _gh_get(url: str) -> dict | list | None:
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        if e.code != 404:
            print(f"[warn] {url}: HTTP {e.code}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"[warn] {url}: {e}", file=sys.stderr)
        return None


def gh_latest_tag(repo: str) -> str | None:
    """Сначала пробуем releases/latest (есть changelog). Если 404 — git tags API
    (fallback для репо с тегами но без GitHub Releases — например amneziawg-go)."""
    rel = _gh_get(f"https://api.github.com/repos/{repo}/releases/latest")
    if isinstance(rel, dict) and rel.get("tag_name"):
        return rel["tag_name"]
    tags = _gh_get(f"https://api.github.com/repos/{repo}/tags?per_page=1")
    if isinstance(tags, list) and tags:
        return tags[0].get("name")
    return None


def main() -> int:
    bumps = []
    for engine, script, var, repo, _ in TARGETS:
        pinned = read_pinned(script, var) if script and var else None
        latest = gh_latest_tag(repo)
        if not latest:
            continue
        if pinned is None:
            # byedpi и подобные — нет явного VERSION. Просто записать latest для info.
            bumps.append({
                "engine": engine, "repo": repo, "pinned": "(unpinned, manual)",
                "latest": latest, "is_bump": False,
            })
            continue
        if pinned != latest:
            bumps.append({
                "engine": engine, "repo": repo, "pinned": pinned,
                "latest": latest, "is_bump": True,
            })

    real_bumps = [b for b in bumps if b["is_bump"]]
    out = ROOT / "upstream-bumps.md"
    if not real_bumps:
        out.write_text("# Upstream check\n\nНикаких bump-ов не найдено.\n")
        print("no bumps")
        _set_github_env("HAS_BUMPS", "false")
        return 0

    lines = ["# Upstream version bumps detected\n",
             "Скрипт `build-tools/check_upstream.py` нашёл свежие upstream-теги.\n",
             "Перед merge: проверить changelog upstream на breaking changes.\n",
             "## Bumps\n"]
    for b in real_bumps:
        lines.append(f"- **{b['engine']}** ([{b['repo']}](https://github.com/{b['repo']}/releases)): "
                     f"`{b['pinned']}` → `{b['latest']}`")
    lines.append("")
    lines.append("## Что делать дальше\n")
    lines.append("1. Открыть changelog upstream между двумя тегами.")
    lines.append("2. Убедиться нет breaking API changes (gomobile bind / .so layout).")
    lines.append("3. Bump'нуть переменную в `build-tools/build_<engine>.sh`.")
    lines.append("4. `gh workflow run binaries.yml --ref <branch> -f artifact=<engine>`.")
    lines.append("5. После публикации release — `regen_lock.py` обновляет `binaries.lock.yaml`.")
    out.write_text("\n".join(lines))

    print(f"found {len(real_bumps)} bumps")
    for b in real_bumps:
        print(f"  {b['engine']}: {b['pinned']} → {b['latest']}")
    _set_github_env("HAS_BUMPS", "true")
    return 0


def _set_github_env(key: str, value: str) -> None:
    env_file = os.environ.get("GITHUB_ENV")
    if env_file:
        with open(env_file, "a") as f:
            f.write(f"{key}={value}\n")


if __name__ == "__main__":
    sys.exit(main())
