from __future__ import annotations

import asyncio
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

MEMORY_DIR_NAME = ".memory"
SAFE_ENV_NAMES = {"PATH", "SYSTEMROOT", "WINDIR", "COMSPEC", "PATHEXT", "TMP", "TEMP", "TMPDIR"}

DEFAULT_CODEX_TIMEOUT = 900


def selected_backend() -> str:
    return "codex"


def _codex_command(cwd: Path, sandbox: str, output_file: Path) -> list[str]:
    codex = find_codex_cli()
    return [
        codex,
        "exec",
        "--ephemeral",
        "--skip-git-repo-check",
        "--ignore-rules",
        "-c",
        "features.hooks=false",
        "-s",
        sandbox,
        "-C",
        str(cwd),
        "-o",
        str(output_file),
        "-",
    ]


def find_codex_cli() -> str:
    configured = os.environ.get("CODEX_CLI_PATH")
    if configured and Path(configured).exists():
        return configured

    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        candidates = sorted(
            Path(local_app_data).glob("OpenAI/Codex/bin/*/codex.exe"),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        )
        if candidates:
            return str(candidates[0])

    resolved = shutil.which("codex.exe") or shutil.which("codex")
    if resolved:
        return resolved

    return "codex"


def _safe_env(home: Path) -> dict[str, str]:
    env = {key: value for key, value in os.environ.items() if key.upper() in SAFE_ENV_NAMES}
    env["CODEX_INVOKED_BY"] = "wiki"
    env["HOME"] = str(home)
    env["USERPROFILE"] = str(home)
    return env


def _prepare_workspace(cwd: Path, sandbox: str, root: Path) -> tuple[Path, Path | None]:
    if cwd.name != MEMORY_DIR_NAME:
        return cwd, None

    workspace = root / MEMORY_DIR_NAME

    def ignore_logs(path: str, names: list[str]) -> set[str]:
        if Path(path).name == "scripts":
            return {name for name in names if name.endswith(".log") or name == "last-flush.json"}
        return set()

    shutil.copytree(cwd, workspace, ignore=ignore_logs)
    return workspace, workspace if sandbox == "workspace-write" else None


def _sync_memory_output(source: Path, target: Path) -> None:
    for name in ("knowledge", "daily"):
        source_path = source / name
        if source_path.exists():
            target_path = target / name
            if target_path.exists():
                shutil.rmtree(target_path)
            shutil.copytree(source_path, target_path)


def _run_codex(prompt: str, cwd: Path, sandbox: str) -> str:
    timeout = int(os.environ.get("WIKI_CODEX_TIMEOUT", str(DEFAULT_CODEX_TIMEOUT)))

    with tempfile.TemporaryDirectory(prefix="wiki-codex-") as tmp:
        tmp_path = Path(tmp)
        home = tmp_path / "home"
        home.mkdir()
        run_cwd, sync_from = _prepare_workspace(cwd, sandbox, tmp_path)
        output_path = tmp_path / "output.md"

        result = subprocess.run(
            _codex_command(run_cwd, sandbox, output_path),
            input=prompt,
            text=True,
            encoding="utf-8",
            capture_output=True,
            cwd=str(run_cwd),
            env=_safe_env(home),
            timeout=timeout,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError((result.stderr or result.stdout or f"codex exited {result.returncode}").strip())

        if sync_from is not None:
            _sync_memory_output(sync_from, cwd)

        if output_path.exists():
            output = output_path.read_text(encoding="utf-8").strip()
            if output:
                return output

        return result.stdout.strip()


async def run_text_prompt(prompt: str, cwd: Path) -> str:
    return await asyncio.to_thread(_run_codex, prompt, cwd, "read-only")


async def run_edit_prompt(prompt: str, cwd: Path) -> float:
    await asyncio.to_thread(_run_codex, prompt, cwd, "workspace-write")
    return 0.0


async def run_workspace_text_prompt(prompt: str, cwd: Path) -> tuple[str, float]:
    response = await asyncio.to_thread(_run_codex, prompt, cwd, "workspace-write")
    return response, 0.0
