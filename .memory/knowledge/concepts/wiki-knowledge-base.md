---
title: "Wiki Knowledge Base Setup"
aliases: [memory-system, claude-memory-compiler, knowledge-vault]
tags: [tooling, knowledge-management, wiki]
sources:
  - "daily/2026-04-29.md"
created: 2026-04-29
updated: 2026-04-29
---

# Wiki Knowledge Base Setup

Ozero uses a personal knowledge base system based on the claude-memory-compiler architecture. The system lives in `.memory/` at the project root and automatically captures conversation context, compiles it into structured wiki articles, and provides index-guided retrieval. It was initialized via the `/wiki-init` skill in v1.0.5.

## Key Points

- The knowledge base lives in `.memory/` with `daily/`, `knowledge/`, `scripts/`, and `hooks/` subdirectories
- Hooks in `.claude/settings.json` fire on session start/end and pre-compact events
- Daily logs are the immutable source; knowledge articles are the compiled output
- The system uses Claude Agent SDK for compilation, not RAG — index-guided retrieval at personal scale
- `.gitignore` is configured to include `.memory/` in version control while excluding runtime state

## Details

The knowledge base follows Andrej Karpathy's LLM knowledge base architecture, adapted for personal use with Claude Code. Instead of ingesting external articles, it compiles knowledge from AI coding sessions. The compiler analogy is central: daily logs are source code, the LLM is the compiler, and knowledge articles are the executable output.

The setup was performed using the `/wiki-init` skill, which cloned `coleam00/claude-memory-compiler` into `.memory/`, configured session hooks in `.claude/settings.json`, and set up `.gitignore` rules. The hooks automatically capture conversation transcripts at session boundaries and before context window compaction, ensuring no knowledge is lost to summarization.

The system supports three operations: compile (daily logs to knowledge articles), query (ask the knowledge base), and lint (health checks for consistency). All operations use the Claude Agent SDK with tool access for file operations.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - The CI workflow that the wiki documents and tracks
- [[concepts/release-process]] - Release decisions captured in daily logs and compiled into articles

## Sources

- [[daily/2026-04-29.md]] - `/wiki-init` executed, .memory/ + hooks + .gitignore configured
