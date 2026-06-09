---
title: "wiki-find Backend Dependency Drift"
sources:
  - "daily/2026-05-10.md"
created: 2026-06-09
updated: 2026-06-09
---

# wiki-find Backend Dependency Drift

`wiki-find` is part of the required investigation flow, so its Python backend dependencies must be kept runnable in the Codex environment. If it fails with a missing backend module, agents should fall back to direct knowledge-file reads for the current task and record the tooling drift for repair.

## Key Points

- The Ozero investigation order requires `wiki-find <topic>` before new hypotheses.
- On 2026-05-10, `wiki-find` failed with `ModuleNotFoundError: No module named 'claude_agent_sdk'`.
- Direct reading of `knowledge/` articles is the correct immediate fallback.
- The tool failure should remain an action item because it weakens retrieval-first discipline.
- This belongs to the same operational layer as [[concepts/wiki-knowledge-base]] and [[concepts/memory-hook-postcommit-dirty-contract]].

## Details

The daily log recorded that `wiki-find` was attempted during CI diagnosis but failed because the Python runtime lacked `claude_agent_sdk`. The assistant then read relevant knowledge files directly. That preserved the investigation flow enough to continue, but it bypassed the intended retrieval interface.

The durable rule is to treat wiki tooling failures as environment drift, not as permission to skip memory. For the active session, direct file reads are acceptable fallback evidence. After the task, the missing dependency should be fixed so future investigations can start from `wiki-find` as required by AGENTS.md.

## Related Concepts

- [[concepts/wiki-knowledge-base]] - Memory wiki operational model and hook reliability.
- [[concepts/memory-hook-postcommit-dirty-contract]] - Memory tooling can affect git workflow and must be handled deliberately.
- [[concepts/real-path-grounding-before-fix-plan]] - Direct file reads are an acceptable grounding fallback.
- [[concepts/ci-artifact-report-driven-debugging]] - CI diagnosis should rely on concrete evidence, not memory guesses.

## Sources

- [[daily/2026-05-10.md]] - Session 14:00: `wiki-find` failed with missing `claude_agent_sdk`; assistant read knowledge files directly as fallback and left repair as an action item.
