---
title: "Wiki Knowledge Base Setup"
aliases: [memory-system, claude-memory-compiler, knowledge-vault]
tags: [tooling, knowledge-management, wiki]
sources:
  - "daily/2026-04-29.md"
  - "daily/2026-04-30.md"
  - "daily/2026-05-01.md"
created: 2026-04-29
updated: 2026-05-01
---

# Wiki Knowledge Base Setup

Ozero uses a personal knowledge base system based on the claude-memory-compiler architecture. The system lives in `.memory/` at the project root and automatically captures conversation context, compiles it into structured wiki articles, and provides index-guided retrieval. It was initialized via the `/wiki-init` skill in v1.0.5.

## Key Points

- The knowledge base lives in `.memory/` with `daily/`, `knowledge/`, `scripts/`, and `hooks/` subdirectories
- Hooks in `.claude/settings.json` fire on session start/end and pre-compact events
- Daily logs are the immutable source; knowledge articles are the compiled output
- The system uses Claude Agent SDK for compilation, not RAG — index-guided retrieval at personal scale
- `.gitignore` is configured to include `.memory/` in version control while excluding runtime state
- Auto-flush hooks (`session-end.py`, `pre-compact.py`) are unreliable — may write "Nothing worth saving" even in productive sessions; daily logs require manual verification before compile

## Details

The knowledge base follows Andrej Karpathy's LLM knowledge base architecture, adapted for personal use with Claude Code. Instead of ingesting external articles, it compiles knowledge from AI coding sessions. The compiler analogy is central: daily logs are source code, the LLM is the compiler, and knowledge articles are the executable output.

The setup was performed using the `/wiki-init` skill, which cloned `coleam00/claude-memory-compiler` into `.memory/`, configured session hooks in `.claude/settings.json`, and set up `.gitignore` rules. The hooks automatically capture conversation transcripts at session boundaries and before context window compaction, ensuring no knowledge is lost to summarization.

The system supports three operations: compile (daily logs to knowledge articles), query (ask the knowledge base), and lint (health checks for consistency). All operations use the Claude Agent SDK with tool access for file operations.

### Operational Lessons (2026-04-30)

The auto-flush hooks proved unreliable during the v0.0.1 development day. Despite 14 flush attempts across a highly productive session (9 VPN fixes, native debugging, architecture research), all hooks wrote "Nothing worth saving" or threw errors. The entire day's knowledge had to be manually transcribed into the daily log before compilation. This revealed a structural weakness: the flush process relies on Claude Agent SDK to judge conversation value, but short or fragmented transcripts (common during rapid iteration) may not contain enough context for accurate value assessment.

The correct operational sequence for wiki maintenance is: compact first (creates new articles from daily logs), then clear/lint second (validates the newly created articles). Running lint before compact produces false positives for broken links — articles referenced in daily logs haven't been created yet.

Broken links in compile output are expected when a daily log references concepts that will become articles during that same compile pass. The compiler creates articles sequentially, so early articles may reference later ones before they exist. A post-compile lint resolves these.

### Continued Flush Failures (2026-05-01)

The flush hook unreliability continued on 2026-05-01. Three flush attempts across two productive sessions (pre-release fix wave + URnetwork SDK integration) all failed: one with "Control request timeout: initialize" and two with "Command failed with exit code 1." The pattern is now confirmed across three consecutive days of use — auto-flush hooks are structurally unreliable and daily logs must be maintained manually or via explicit `/wiki-compact` invocations. The error types vary (timeout, exit code 1, "nothing worth saving") suggesting multiple independent failure modes in the flush pipeline.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - The CI workflow that the wiki documents and tracks
- [[concepts/release-process]] - Release decisions captured in daily logs and compiled into articles

## Sources

- [[daily/2026-04-29.md]] - `/wiki-init` executed, .memory/ + hooks + .gitignore configured
- [[daily/2026-04-30.md]] - Auto-flush hook unreliability discovered; compact-before-clear ordering established; 14 failed flushes in productive session
- [[daily/2026-05-01.md]] - Continued flush failures (3/3 failed: timeout + exit code 1); pattern confirmed across 3 consecutive days
