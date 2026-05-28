# Codex Memory Compiler

Local wiki for AI coding sessions.

## How It Works

```
Codex session JSONL -> .codex hooks -> .memory/hooks/*
  -> scripts/flush.py -> daily/YYYY-MM-DD.md
  -> scripts/compile.py -> knowledge/index.md + concepts/
  -> SessionStart injects index back into Codex
```

## Runtime

- Primary backend: Codex CLI through `codex exec`.
- Legacy backend: Claude Agent SDK only when `WIKI_LLM_BACKEND=claude`.
- Hooks are configured in project `.codex/hooks.json`.
- Claude hooks in `.claude/settings.json` may remain for compatibility but are no longer the primary path.

## Key Commands

```bash
uv run python scripts/query.py "question"
uv run python scripts/query.py "question" --file-back
uv run python scripts/compile.py
uv run python scripts/compile.py --dry-run
uv run python scripts/lint.py --structural-only
uv run python scripts/lint.py
```

## Hook Contract

Codex stores sessions as JSONL under `$CODEX_HOME/sessions/`.

Supported transcript formats:
- Claude: `message.role` + `message.content`
- Codex: `payload.type=message` + `payload.role` + `input_text/output_text`

If hook input has no `transcript_path`, hooks discover the latest Codex transcript by `session_id` or `cwd`.

## State

- `daily/` is append-only source material.
- `knowledge/` is compiled wiki output.
- `scripts/state.json` tracks compiled daily-log hashes.
- `scripts/last-flush.json` deduplicates flushes.
- `scripts/*.log` are operational logs.

## Backend Notes

`scripts/llm_backend.py` finds the real Codex executable in this order:
- `CODEX_CLI_PATH`
- `%LOCALAPPDATA%/OpenAI/Codex/bin/*/codex.exe`
- `codex.exe` or `codex` on PATH

This avoids the WindowsApps execution-alias trap where Python subprocess can fail with access denied.
