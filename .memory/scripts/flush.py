"""
Memory flush agent - extracts important knowledge from conversation context.

Spawned by session-end.py or pre-compact.py as a background process. Reads
pre-extracted conversation context from a .md file, uses Codex CLI
to decide what's worth saving, and appends the result to today's daily log.

Usage:
    uv run python flush.py <context_file.md> <session_id>
"""

from __future__ import annotations

# Recursion prevention: set this BEFORE any imports that might invoke Codex.
import os
os.environ["CODEX_INVOKED_BY"] = "memory_flush"

import asyncio
import hashlib
import json
import logging
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

from llm_backend import run_text_prompt, selected_backend

ROOT = Path(__file__).resolve().parent.parent
DAILY_DIR = ROOT / "daily"
SCRIPTS_DIR = ROOT / "scripts"
STATE_FILE = SCRIPTS_DIR / "last-flush.json"
LOG_FILE = SCRIPTS_DIR / "flush.log"
STOP_FLUSH_THROTTLE_SECONDS = 2 * 60 * 60
COMPILE_TRIGGER_THROTTLE_SECONDS = 2 * 60 * 60
RECENT_CONTEXT_HASH_TTL_SECONDS = 7 * 24 * 60 * 60

# Set up file-based logging so we can verify the background process ran.
# The parent process sends stdout/stderr to DEVNULL (to avoid the inherited
# file handle bug on Windows), so this is our only observability channel.
logging.basicConfig(
    filename=str(LOG_FILE),
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)


def load_flush_state() -> dict:
    if STATE_FILE.exists():
        try:
            return json.loads(STATE_FILE.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            pass
    return {}


def save_flush_state(state: dict) -> None:
    STATE_FILE.write_text(json.dumps(state), encoding="utf-8")


def context_source(context_file: Path) -> str:
    if context_file.name.startswith("session-flush-"):
        return "stop"
    if context_file.name.startswith("flush-context-"):
        return "pre-compact"
    return "unknown"


def context_hash(context: str) -> str:
    return hashlib.sha256(context.encode("utf-8")).hexdigest()[:16]


def recent_context_hashes(state: dict, now_ts: float) -> dict:
    recent = state.get("recent_context_hashes", {})
    if not isinstance(recent, dict):
        return {}
    return {
        str(key): float(value)
        for key, value in recent.items()
        if isinstance(value, (int, float)) and now_ts - float(value) < RECENT_CONTEXT_HASH_TTL_SECONDS
    }


def append_to_daily_log(content: str, section: str = "Session") -> None:
    """Append content to today's daily log."""
    today = datetime.now(timezone.utc).astimezone()
    log_path = DAILY_DIR / f"{today.strftime('%Y-%m-%d')}.md"

    if not log_path.exists():
        DAILY_DIR.mkdir(parents=True, exist_ok=True)
        log_path.write_text(
            f"# Daily Log: {today.strftime('%Y-%m-%d')}\n\n## Sessions\n\n## Memory Maintenance\n\n",
            encoding="utf-8",
        )

    time_str = today.strftime("%H:%M")
    entry = f"### {section} ({time_str})\n\n{content}\n\n"

    with open(log_path, "a", encoding="utf-8") as f:
        f.write(entry)


async def run_flush(context: str) -> str:
    """Extract important knowledge from conversation context."""
    prompt = f"""Review the conversation context below and respond with a concise summary
of important items that should be preserved in the daily log.
Do NOT use any tools — just return plain text.

Format your response as a structured daily log entry with these sections:

**Context:** [One line about what the user was working on]

**Key Exchanges:**
- [Important Q&A or discussions]

**Decisions Made:**
- [Any decisions with rationale]

**Lessons Learned:**
- [Gotchas, patterns, or insights discovered]

**Action Items:**
- [Follow-ups or TODOs mentioned]

Skip anything that is:
- Routine tool calls or file reads
- Content that's trivial or obvious
- Trivial back-and-forth or clarification exchanges

Only include sections that have actual content. If nothing is worth saving,
respond with exactly: FLUSH_OK

## Conversation Context

{context}"""

    try:
        return await run_text_prompt(prompt, ROOT)
    except Exception as e:
        import traceback
        logging.error("LLM backend error: %s\n%s", e, traceback.format_exc())
        return f"FLUSH_ERROR: {type(e).__name__}: {e}"


COMPILE_AFTER_HOUR = 18  # 6 PM local time


def maybe_trigger_compilation() -> None:
    """If it's past the compile hour and today's log hasn't been compiled, run compile.py."""
    import subprocess as _sp

    now = datetime.now(timezone.utc).astimezone()
    if now.hour < COMPILE_AFTER_HOUR:
        return

    # Check if today's log has already been compiled
    today_log = f"{now.strftime('%Y-%m-%d')}.md"
    compile_state_file = SCRIPTS_DIR / "state.json"
    if compile_state_file.exists():
        try:
            compile_state = json.loads(compile_state_file.read_text(encoding="utf-8"))
            ingested = compile_state.get("ingested", {})
            if today_log in ingested:
                # Already compiled today - check if the log has changed since
                from hashlib import sha256
                log_path = DAILY_DIR / today_log
                if log_path.exists():
                    current_hash = sha256(log_path.read_bytes()).hexdigest()[:16]
                    if ingested[today_log].get("hash") == current_hash:
                        return  # log unchanged since last compile
        except (json.JSONDecodeError, OSError):
            pass

    compile_script = SCRIPTS_DIR / "compile.py"
    if not compile_script.exists():
        return

    state = load_flush_state()
    last_compile_trigger = state.get("last_compile_trigger", {})
    if isinstance(last_compile_trigger, dict):
        last_ts = last_compile_trigger.get("timestamp", 0)
        if isinstance(last_ts, (int, float)) and time.time() - float(last_ts) < COMPILE_TRIGGER_THROTTLE_SECONDS:
            logging.info("Skipping compile trigger: throttled")
            return

    logging.info("End-of-day compilation triggered (after %d:00)", COMPILE_AFTER_HOUR)

    cmd = ["uv", "run", "--directory", str(ROOT), "python", str(compile_script)]

    kwargs: dict = {}
    if sys.platform == "win32":
        kwargs["creationflags"] = _sp.CREATE_NEW_PROCESS_GROUP | _sp.DETACHED_PROCESS
    else:
        kwargs["start_new_session"] = True

    try:
        log_handle = open(str(SCRIPTS_DIR / "compile.log"), "a")
        _sp.Popen(cmd, stdout=log_handle, stderr=_sp.STDOUT, cwd=str(ROOT), **kwargs)
        state["last_compile_trigger"] = {"timestamp": time.time(), "date": now.strftime("%Y-%m-%d")}
        save_flush_state(state)
    except Exception as e:
        logging.error("Failed to spawn compile.py: %s", e)


def main():
    if len(sys.argv) < 3:
        logging.error("Usage: %s <context_file.md> <session_id>", sys.argv[0])
        sys.exit(1)

    context_file = Path(sys.argv[1])
    session_id = sys.argv[2]

    logging.info("flush.py started for session %s, context: %s", session_id, context_file)

    if not context_file.exists():
        logging.error("Context file not found: %s", context_file)
        return

    # Read pre-extracted context
    context = context_file.read_text(encoding="utf-8").strip()
    if not context:
        logging.info("Context file is empty, skipping")
        context_file.unlink(missing_ok=True)
        return

    state = load_flush_state()
    now_ts = time.time()
    source = context_source(context_file)
    content_hash = context_hash(context)
    recent_hashes = recent_context_hashes(state, now_ts)

    if content_hash in recent_hashes:
        logging.info("Skipping duplicate context for session %s (%s)", session_id, source)
        state["recent_context_hashes"] = recent_hashes
        save_flush_state(state)
        context_file.unlink(missing_ok=True)
        return

    stop_flushes = state.get("stop_flushes", {})
    if not isinstance(stop_flushes, dict):
        stop_flushes = {}
    last_stop_flush = stop_flushes.get(session_id, {})
    if (
        source == "stop"
        and isinstance(last_stop_flush, dict)
        and isinstance(last_stop_flush.get("timestamp"), (int, float))
        and now_ts - float(last_stop_flush["timestamp"]) < STOP_FLUSH_THROTTLE_SECONDS
    ):
        logging.info("Skipping Stop flush for session %s: throttled", session_id)
        state["recent_context_hashes"] = recent_hashes
        state["stop_flushes"] = stop_flushes
        save_flush_state(state)
        context_file.unlink(missing_ok=True)
        return

    logging.info("Flushing session %s: %d chars via backend=%s", session_id, len(context), selected_backend())

    # Run the LLM extraction
    response = asyncio.run(run_flush(context))

    # Append to daily log
    if "FLUSH_OK" in response:
        logging.info("Result: FLUSH_OK")
        append_to_daily_log(
            "FLUSH_OK - Nothing worth saving from this session", "Memory Flush"
        )
    elif "FLUSH_ERROR" in response:
        logging.error("Result: %s", response)
        append_to_daily_log(response, "Memory Flush")
    else:
        logging.info("Result: saved to daily log (%d chars)", len(response))
        append_to_daily_log(response, "Session")

    # Update dedup and throttle state.
    now_ts = time.time()
    recent_hashes[content_hash] = now_ts
    state["recent_context_hashes"] = recent_hashes
    state["last_flush"] = {
        "session_id": session_id,
        "timestamp": now_ts,
        "context_hash": content_hash,
        "source": source,
    }
    if source == "stop":
        stop_flushes[session_id] = {"timestamp": now_ts, "context_hash": content_hash}
        state["stop_flushes"] = stop_flushes
    save_flush_state(state)

    # Clean up context file
    context_file.unlink(missing_ok=True)

    # End-of-day auto-compilation: if it's past the compile hour and today's
    # log hasn't been compiled yet, trigger compile.py in the background.
    maybe_trigger_compilation()

    logging.info("Flush complete for session %s", session_id)


if __name__ == "__main__":
    main()
