---
title: CI required check name preservation
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# CI required check name preservation

## Key Points
- CI repairs must preserve required job ids and check names unless branch protection is intentionally changed.
- GitHub Actions syntax fixes should be minimal when the failure is a workflow parse error.
- Multiline job `if:` expressions can prevent jobs from being created at all, so the repair target is parser validity before test behavior.
- Workflow edits should avoid unrelated trigger, job, or artifact churn while runtime fixes are still in progress.

## Details

The 2026-05-29 CI repair found that GitHub Actions failed before job creation because `ci.yml` contained multiline `${{ ... }}` expressions in job-level `if:` clauses. The fix was to rewrite those expressions as single-line conditions while keeping the same pull request trigger and required check names. This is a concrete instance of [[concepts/github-actions-multiline-if-parse-failure]].

Required check preservation mattered because branch protection can depend on exact job names. Renaming or deleting jobs while debugging a parser failure introduces a second variable: CI may become green locally in the workflow file but no longer satisfy repository policy. This connects to [[concepts/ci-workflow-discipline]] and [[concepts/github-actions-run-id-monitoring]], where CI diagnosis is anchored to concrete runs and stable gates.

The same session also clarified that `.memory` updates should not be pushed as standalone routine commits during active code repair. That policy belongs primarily to [[concepts/memory-commit-with-work-only]], but it reinforces the CI rule: avoid workflow or history noise while required checks are being restored.

## Related Concepts
- [[concepts/github-actions-multiline-if-parse-failure]]
- [[concepts/ci-workflow-discipline]]
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/memory-commit-with-work-only]]

## Sources
- [[daily/2026-05-29]] records that CI failed before job creation because of multiline `${{ ... }}` job `if` expressions.
- [[daily/2026-05-29]] records the chosen fix direction: keep pull request trigger and required job ids, but make each `if` expression single-line.
- [[daily/2026-05-29]] records the instruction to avoid separate `.memory` push steps during active repair work.
