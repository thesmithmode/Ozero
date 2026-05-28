# AGENTS.md - Codex Memory Wiki Schema

## Purpose

`.memory/` turns AI coding sessions into a local markdown knowledge base.

```
daily/ -> compile -> knowledge/ -> query -> SessionStart context
```

## Runtime Contract

- Runtime is Codex CLI through `scripts/llm_backend.py`.
- Project hooks live in `.codex/hooks.json`.
- Hook scripts accept Codex transcript JSONL.
- Codex messages: `payload.type=message`, `payload.role`, `input_text/output_text`.
- If hook input has no `transcript_path`, discover the latest Codex transcript by `session_id` or `cwd`.

## Structure

```
.memory/
|-- daily/
|-- knowledge/
|   |-- index.md
|   |-- log.md
|   |-- concepts/
|   |-- connections/
|   |-- qa/
|-- hooks/
|-- scripts/
```

## Daily Logs

- `daily/` is append-only source material.
- Never rewrite old daily facts to make them look cleaner.
- New sessions use this shape:

```markdown
# Daily Log: YYYY-MM-DD

## Sessions

### Session (HH:MM)

**Context:** One-line context.

**Key Exchanges:**
- Important user/assistant exchange.

**Decisions Made:**
- Decision and rationale.

**Lessons Learned:**
- Reusable gotcha or rule.

**Action Items:**
- Follow-up if any.
```

## Knowledge Files

- `knowledge/index.md` is the primary retrieval catalog.
- `knowledge/log.md` is the append-only build log.
- `knowledge/concepts/` stores atomic facts, decisions, bugs, patterns, and lessons.
- `knowledge/connections/` stores non-obvious links between concepts.
- `knowledge/qa/` stores answers created by `query.py --file-back`.
- Every article needs YAML frontmatter with `title`, `sources`, `created`, `updated`.
- Every factual claim should trace back to `[[daily/YYYY-MM-DD.md]]`.

## Compile Rules

- Read `knowledge/index.md` before changing knowledge articles.
- Prefer updating existing articles over creating duplicates.
- Create concepts only for reusable knowledge.
- Keep articles factual, concise, and source-linked.
- Use Obsidian `[[wikilinks]]` without `.md`.
- Update `knowledge/index.md` and append `knowledge/log.md`.

## Query Rules

- Read `knowledge/index.md` first.
- Select relevant articles from the index, then read them fully.
- Cite answers with `[[wikilinks]]`.
- If no relevant memory exists, say so.
- With `--file-back`, create a Q&A article and update index/log.

## Lint Rules

`scripts/lint.py` checks:
- broken links
- orphan pages
- orphan sources
- stale articles
- missing backlinks
- sparse articles
- contradictions when full lint is requested

## Operational Rules

- `flush.py` extracts only durable context worth remembering.
- `compile.py --dry-run` is the safe preflight for pending daily logs.
- `lint.py --structural-only` is the cheap health gate.
- Do not store secrets, personal paths, or private hostnames in daily/knowledge.
- `.memory/` changes must be committed together with the related work.
