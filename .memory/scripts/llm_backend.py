from __future__ import annotations

import asyncio
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

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


def _run_codex(prompt: str, cwd: Path, sandbox: str) -> str:
    timeout = int(os.environ.get("WIKI_CODEX_TIMEOUT", str(DEFAULT_CODEX_TIMEOUT)))
    env = os.environ.copy()
    env["CODEX_INVOKED_BY"] = "wiki"

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False, suffix=".md") as out:
        output_path = Path(out.name)

    try:
        result = subprocess.run(
            _codex_command(cwd, sandbox, output_path),
            input=prompt,
            text=True,
            encoding="utf-8",
            capture_output=True,
            cwd=str(cwd),
            env=env,
            timeout=timeout,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError((result.stderr or result.stdout or f"codex exited {result.returncode}").strip())

        if output_path.exists():
            output = output_path.read_text(encoding="utf-8").strip()
            if output:
                return output

        return result.stdout.strip()
    finally:
        output_path.unlink(missing_ok=True)


async def run_text_prompt(prompt: str, cwd: Path) -> str:
    return await asyncio.to_thread(_run_codex, prompt, cwd, "read-only")


async def run_edit_prompt(prompt: str, cwd: Path) -> float:
    await asyncio.to_thread(_run_codex, prompt, cwd, "workspace-write")
    return 0.0


async def run_workspace_text_prompt(prompt: str, cwd: Path) -> tuple[str, float]:
    response = await asyncio.to_thread(_run_codex, prompt, cwd, "workspace-write")
    return response, 0.0
