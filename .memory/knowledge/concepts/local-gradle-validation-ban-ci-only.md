---
title: Local Gradle Validation Ban and CI-Only Signal
sources:
  - daily/2026-05-31.md
created: 2026-06-01
updated: 2026-06-01
---
# Local Gradle Validation Ban and CI-Only Signal

## Key Points
- Ozero forbids local Gradle, lint, and test tasks when they can overload the workstation; validation must come from GitHub Actions.
- Local work may still use read-only inspection and lightweight static checks such as diff review when they do not invoke Gradle.
- CI failures must be diagnosed from workflow logs, artifacts, snapshots, and code contracts rather than reproduced locally.
- Dirty `.memory/` changes from hooks are bundled with the nearest related work commit, not committed as standalone routine work.

## Details

On 2026-05-31 the user clarified that even quick local `ktlintCheck`, `detekt`, Gradle build, and test tasks are forbidden for Ozero. Earlier sessions still allowed "fast static gates"; the corrected rule is stricter: local validation is read-only code inspection plus non-Gradle checks, while all executable validation goes through GitHub Actions.

This changes the debugging workflow. When `gh` or API access is unavailable, the agent must not compensate by running local tests. The fallback is to narrow failure risk through `ci.yml`, local git history, known memory articles, committed test contracts, and available CI artifacts or snapshots. The rule connects directly to [[concepts/github-actions-run-id-monitoring]] and [[concepts/ci-artifact-report-driven-debugging]] because terminal CI status and real artifacts become the primary evidence source.

## Related Concepts
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/ci-workflow-discipline]]
- [[concepts/memory-commit-with-work-only]]

## Sources
- [[daily/2026-05-31]]: The user repeatedly prohibited local tests and later corrected the rule to ban local Gradle/lint/test checks entirely.
- [[daily/2026-05-31]]: The CI-debug sessions used artifacts, workflow analysis, memory, and static review when GitHub API or exact logs were unavailable.
