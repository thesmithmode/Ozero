from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

MAX_DISCOVERY_FILES = 80


def extract_text(content: Any) -> str:
    if isinstance(content, str):
        return content

    if not isinstance(content, list):
        return ""

    text_parts: list[str] = []
    for block in content:
        if isinstance(block, dict):
            block_type = block.get("type")
            if block_type in ("text", "input_text", "output_text"):
                text_parts.append(str(block.get("text", "")))
            elif block_type == "function_call_output":
                text_parts.append(str(block.get("output", "")))
        elif isinstance(block, str):
            text_parts.append(block)

    return "\n".join(part for part in text_parts if part)


def extract_message(entry: dict[str, Any]) -> tuple[str, str]:
    payload = entry.get("payload", {})
    if isinstance(payload, dict) and payload.get("type") == "message":
        return str(payload.get("role", "")), extract_text(payload.get("content", ""))

    return "", ""


def is_noise_turn(role: str, content: str, include_tool_output: bool) -> bool:
    if role not in ("user", "assistant"):
        return True

    stripped = content.strip()
    if not stripped:
        return True

    if stripped.startswith("# AGENTS.md instructions for "):
        return True

    if stripped.startswith("<environment_context>"):
        return True

    if stripped.startswith("<local-command-caveat>"):
        return True

    if not include_tool_output and (
        stripped.startswith("<local-command-stdout>")
        or stripped.startswith("<local-command-stderr>")
        or stripped.startswith("<command-name>")
    ):
        return True

    return False


def extract_conversation_context(
    transcript_path: Path,
    *,
    max_turns: int,
    max_context_chars: int,
    include_tool_output: bool = False,
) -> tuple[str, int]:
    turns: list[str] = []

    with open(transcript_path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue

            role, content = extract_message(entry)
            if is_noise_turn(role, content, include_tool_output):
                continue

            label = "User" if role == "user" else "Assistant"
            turns.append(f"**{label}:** {content.strip()}\n")

    recent = turns[-max_turns:]
    context = "\n".join(recent)

    if len(context) > max_context_chars:
        context = context[-max_context_chars:]
        boundary = context.find("\n**")
        if boundary > 0:
            context = context[boundary + 1 :]

    return context, len(recent)


def discover_codex_transcript(session_id: str, cwd: str | None) -> Path | None:
    codex_home = Path(os.environ.get("CODEX_HOME", Path.home() / ".codex"))
    sessions_dir = codex_home / "sessions"
    if not sessions_dir.exists():
        return None

    candidates = sorted(
        sessions_dir.rglob("*.jsonl"),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )[:MAX_DISCOVERY_FILES]

    normalized_cwd = str(Path(cwd).resolve()).lower() if cwd else ""

    for candidate in candidates:
        try:
            with candidate.open(encoding="utf-8") as f:
                first = json.loads(f.readline())
        except (OSError, json.JSONDecodeError):
            continue

        payload = first.get("payload", {})
        if not isinstance(payload, dict):
            continue

        meta = payload if first.get("type") == "session_meta" else payload.get("session_meta", {})
        if not isinstance(meta, dict):
            meta = payload

        candidate_id = str(meta.get("id", ""))
        candidate_cwd = str(meta.get("cwd", ""))
        if session_id and candidate_id == session_id:
            return candidate

        if normalized_cwd and candidate_cwd:
            try:
                if str(Path(candidate_cwd).resolve()).lower() == normalized_cwd:
                    return candidate
            except OSError:
                pass

    return None
