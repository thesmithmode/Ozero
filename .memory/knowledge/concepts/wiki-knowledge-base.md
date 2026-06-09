---
title: "Wiki Knowledge Base Setup"
aliases: [memory-system, claude-memory-compiler, knowledge-vault]
tags: [tooling, knowledge-management, wiki]
sources:
  - "daily/2026-04-29.md"
  - "daily/2026-04-30.md"
  - "daily/2026-05-01.md"
  - "daily/2026-05-20.md"
  - "daily/2026-05-10.md"
created: 2026-04-29
updated: 2026-06-09
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

An API 400 error triggered by an `advisor_tool_result` content block makes a Claude Code session completely non-functional — all subsequent agent responses are blocked. The only recovery is to restart the session. This occurred during the 2026-04-30 wiki maintenance session and is distinct from hook failures or SDK errors.

### Continued Flush Failures (2026-05-01)

The flush hook unreliability continued on 2026-05-01. Three flush attempts across two productive sessions (pre-release fix wave + URnetwork SDK integration) all failed: one with "Control request timeout: initialize" and two with "Command failed with exit code 1." The pattern is now confirmed across three consecutive days of use — auto-flush hooks are structurally unreliable and daily logs must be maintained manually or via explicit `/wiki-compact` invocations. The error types vary (timeout, exit code 1, "nothing worth saving") suggesting multiple independent failure modes in the flush pipeline.

### Flush Failures Pattern (2026-05-20)

On 2026-05-20, `FLUSH_ERROR: exit code 1` occurred 13+ times across the day with no actionable error details in stderr. Interspersed `FLUSH_OK — Nothing worth saving` results from highly productive sessions confirm the hook's value-assessment is unreliable. The pattern: flush processes a truncated JSONL transcript (tool results stripped, no tool calls), Claude sees only assistant messages with no conversation context, concludes there is nothing to save. Sessions with heavy tool use (many file reads/edits) are most vulnerable — the conversation appears one-sided without the tool results.

The operational fix remains the same as established 2026-04-30: treat hooks as unreliable background helpers, not the primary capture mechanism. Manual session notes in daily logs are the authoritative source.

### Contradiction Audit (2026-05-20 18:27 session)

A systematic cross-article contradiction audit of ~140 articles found 9 issues (4 direct contradictions, 5 inconsistencies):

**Direct contradictions:**
1. `dual-go-runtime-eager-loading` claims eager loading "allowed coexistence" but `go-runtime-process-isolation` documents process isolation as the fix — the earlier article implies the wrong conclusion
2. `urnetwork-relay-always` has internal contradiction: relay described as automatic, but requires non-guest `byClientJwt`; conflicts with `urnetwork-guest-mode-relay-blocker`
3. `manual-di-design` recommends eager init; `robolectric-hilt-eager-init-trap` recommends lazy init — opposite advice, no cross-reference
4. `warp-false-connected-no-handshake` documents 5s→10s timeout revert; `warp-uapi-handshake-polling` silently shows 10s as the always-correct value — historical context lost

**Inconsistencies:**
5. `genetic-strategy-evolution` has v2 power-law fitness formula; `granular-probe-fitness-scoring` has gradient function — canonical current formula ambiguous
6. `byedpi-stale-serverfd-unconditional-forceclose` presents guard ownership as always-true historical invariant, omitting earlier versions that released guard — misleading impression
7–9. Minor cross-reference gaps and stale summaries

**Observation:** contradictions cluster around articles that document an *evolution* of approach (eager→process-isolation, guest→non-guest, bad→good practice). Later articles don't always update earlier ones, leaving conflicting claims. Resolution strategy: add `## Historical Note` sections to older articles rather than deleting — preserves the evolution context while making the current state unambiguous.

### Contradiction Resolution and lint.py Overflow Fix (2026-05-20 18:53 session)

`check_contradictions()` in `lint.py` had the same SDK overflow bug as `compile.py`: it dumped all 152 article contents inline in the prompt (~1.2MB) → Claude Agent SDK exited with code 1. Fix applied (commit `38770996`): identical to the compile.py fix — path-list-only prompt, `allowed_tools=[Read, Glob, Grep]`, `max_turns=30` so the LLM reads articles on demand.

The 9 contradictions found in session 18:27 required **three iterative audit rounds** to fully resolve (9→3→3→1→0). Each round: run lint contradictions check → LLM reads flagged pairs → apply fixes → re-run. Contradictions cluster: fixing one article often exposes a previously-hidden inconsistency in a related article. Iterative re-runs until zero findings is the correct termination condition.

`lint.py` transient `LLM check failed: exit 1` from earlier session was a prior-run artifact (unfixed overflow), not a new failure. Standalone re-run after the patch produced NO_ISSUES. Rule: always re-run on transient contradiction failures; a single `exit 1` without stderr content is likely overflow or an artifact from a previous broken run, not a structural issue in the articles themselves.

Duplicate article `urnetwork-filter-locations-trigger.md` was deleted (duplicate of `urnetwork-filterlocations-trigger.md`). 441 missing-backlinks findings were intentionally skipped — back-linking all 152 articles is low-value maintenance work; the index provides the primary retrieval path.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - The CI workflow that the wiki documents and tracks
- [[concepts/release-process]] - Release decisions captured in daily logs and compiled into articles
- [[concepts/wikifind-backend-dependency-drift]] - Tooling drift that can break retrieval-first investigation

## Sources

- [[daily/2026-04-29.md]] - `/wiki-init` executed, .memory/ + hooks + .gitignore configured
- [[daily/2026-04-30.md]] - Auto-flush hook unreliability discovered; compact-before-clear ordering established; 14 failed flushes in productive session
- [[daily/2026-05-01.md]] - Continued flush failures (3/3 failed: timeout + exit code 1); pattern confirmed across 3 consecutive days
- [[daily/2026-05-20.md]] - 13+ FLUSH_ERROR (exit code 1) with no stderr detail; FLUSH_OK "nothing worth saving" from productive sessions; root cause: truncated JSONL transcript (tool results stripped) → Claude sees assistant-only messages; contradiction audit session 18:27 found 9 issues (4 direct contradictions, 5 inconsistencies) across ~140 articles; session 18:53: lint.py overflow fix (path-list+Read on demand), 3-round iterative resolution (9→3→3→1→0), duplicate article deleted, transient re-run rule
- [[daily/2026-05-10.md]] - `wiki-find` failed with missing `claude_agent_sdk`; direct `knowledge/` reads were used as fallback during CI diagnosis
