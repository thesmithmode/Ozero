---
title: "Memory Instruction Token Budget Maintenance"
aliases: [claude-memory-token-budget, agents-token-budget, memory-instruction-cleanup]
tags: [memory, instructions, maintenance, codex, gotcha]
sources:
  - "daily/2026-05-08.md"
created: 2026-06-12
updated: 2026-06-12
---

# Memory Instruction Token Budget Maintenance

Memory and instruction files should be maintained by scope and token impact, not only by line count. Local project instructions, global instructions, rules, and wiki index output have different audiences, so token cleanup must avoid deleting rules that look redundant but protect another project or workflow.

## Key Points

- Global and project instruction files have different scopes; a rule duplicated in meaning may still be required when the global file is used outside Ozero.
- Token optimization should measure practical prompt weight and durable utility, because line count can hide high-token sections and overstate short noisy sections.
- Lists of banned AI-signature substrings should stay explicit because agents use them as a concrete grep checklist before accepting commits.
- CI watcher shell patterns should remain in globally available instructions when project-local rule files are unavailable in other repositories.
- Local memory cleanup can delete stale project findings only after their durable knowledge has moved into `knowledge/` articles.

## Details

The 2026-05-08 maintenance session separated global instructions from local Ozero instructions. The global file applies across all projects, while the local file covers Ozero-specific constraints. A rule that appears redundant in Ozero may be the only available instruction in another repository, so deleting it from the global file based only on local context is unsafe.

The same session treated token budget as the real optimization metric. The local Ozero instruction file was reduced from 107 to 100 lines by replacing verbose product description with a README link and removing dead rule references, while keeping concrete operational checklists. Stale local memory files were removed only after their findings were represented in the compiled wiki.

## Related Concepts

- [[concepts/wiki-knowledge-base]] - Durable project knowledge lives in compiled articles rather than ad hoc memory notes.
- [[concepts/memory-commit-with-work-only]] - Memory edits should be bundled with related work rather than drifting as standalone state.
- [[concepts/ci-workflow-discipline]] - CI watcher rules are operational instructions that must stay available where the watcher is used.

## Sources

- [[daily/2026-05-08.md]] - Session 16:08: global and local instruction files have different scopes; banned AI-signature substrings and CI watcher shell patterns were retained; local Ozero instruction file was reduced from 107 to 100 lines; six stale local memory files were deleted.
