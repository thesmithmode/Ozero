"""
Stop hook - captures conversation transcript for memory extraction.

Codex fires Stop when a turn finishes, not only when a session closes.
This hook reads the transcript path from stdin, extracts conversation
context, and spawns flush.py as a background process. flush.py throttles
Stop-originated flushes so active sessions do not write memory on every turn.

The hook itself does NO API calls - only local file I/O for speed (<10s).
"""

from __future__ import annotations

import logging
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DAILY_DIR = ROOT / "daily"
SCRIPTS_DIR = ROOT / "scripts"
STATE_DIR = SCRIPTS_DIR
sys.path.insert(0, str(SCRIPTS_DIR))

from transcript import discover_codex_transcript, extract_conversation_context

if os.environ.get("CODEX_INVOKED_BY"):
    sys.exit(0)

logging.basicConfig(
    filename=str(SCRIPTS_DIR / "flush.log"),
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [hook] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

MAX_TURNS = 30
MAX_CONTEXT_CHARS = 15_000
MIN_TURNS_TO_FLUSH = 1


def main() -> None:
    # Read hook input from stdin.
    # Windows hook payloads may pass paths with unescaped backslashes.
    try:
        raw_input = sys.stdin.read()
        try:
            hook_input: dict = json.loads(raw_input)
        except json.JSONDecodeError:
            fixed_input = re.sub(r'(?<!\\)\\(?!["\\])', r'\\\\', raw_input)
            hook_input = json.loads(fixed_input)
    except (json.JSONDecodeError, ValueError, EOFError) as e:
        logging.error("Failed to parse stdin: %s", e)
        return

    session_id = hook_input.get("session_id", "unknown")
    source = hook_input.get("source", "unknown")
    transcript_path_str = hook_input.get("transcript_path", "")
    cwd = hook_input.get("cwd") or hook_input.get("workspace_root")

    logging.info("Stop fired: session=%s source=%s", session_id, source)

    if not transcript_path_str or not isinstance(transcript_path_str, str):
        transcript_path = discover_codex_transcript(str(session_id), cwd if isinstance(cwd, str) else None)
        if not transcript_path:
            logging.info("SKIP: no transcript path")
            return
        logging.info("Resolved transcript: %s", transcript_path)
    else:
        transcript_path = Path(transcript_path_str)

    if not transcript_path.exists():
        discovered = discover_codex_transcript(str(session_id), cwd if isinstance(cwd, str) else None)
        if not discovered:
            logging.info("SKIP: transcript missing: %s", transcript_path_str)
            return
        transcript_path = discovered
        logging.info("Resolved transcript after missing path: %s", transcript_path)

    try:
        context, turn_count = extract_conversation_context(
            transcript_path,
            max_turns=MAX_TURNS,
            max_context_chars=MAX_CONTEXT_CHARS,
        )
    except Exception as e:
        logging.error("Context extraction failed: %s", e)
        return

    if not context.strip():
        logging.info("SKIP: empty context")
        return

    if turn_count < MIN_TURNS_TO_FLUSH:
        logging.info("SKIP: only %d turns (min %d)", turn_count, MIN_TURNS_TO_FLUSH)
        return

    # Write context to a temp file for the background process
    timestamp = datetime.now(timezone.utc).astimezone().strftime("%Y%m%d-%H%M%S")
    context_file = STATE_DIR / f"session-flush-{session_id}-{timestamp}.md"
    context_file.write_text(context, encoding="utf-8")

    # Spawn flush.py as a background process
    flush_script = SCRIPTS_DIR / "flush.py"

    cmd = [
        "uv",
        "run",
        "--directory",
        str(ROOT),
        "python",
        str(flush_script),
        str(context_file),
        session_id,
    ]

    # On Windows, use CREATE_NO_WINDOW to avoid flash console window.
    # Do NOT use DETACHED_PROCESS — it breaks the Agent SDK's subprocess I/O.
    creation_flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0

    try:
        subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            creationflags=creation_flags,
        )
        logging.info("Spawned flush.py for session %s (%d turns, %d chars)", session_id, turn_count, len(context))
    except Exception as e:
        logging.error("Failed to spawn flush.py: %s", e)


if __name__ == "__main__":
    main()
