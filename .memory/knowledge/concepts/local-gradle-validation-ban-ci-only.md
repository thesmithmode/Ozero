---
title: Local Gradle Validation Ban and CI-Only Signal
sources:
  - daily/2026-05-31.md
  - daily/2026-06-04.md
created: 2026-06-01
updated: 2026-06-13
---
# Local Gradle Validation Ban and CI-Only Signal

## Key Points
- Ozero forbids local Gradle, lint, and test tasks when they can overload the workstation; validation must come from GitHub Actions.
- Local work may still use read-only inspection and lightweight static checks such as diff review when they do not invoke Gradle.
- A user can explicitly allow local Gradle/lint/test runs for a specific CI-debugging cycle, but that is a scoped exception rather than the default rule.
- CI failures must be diagnosed from workflow logs, artifacts, snapshots, and code contracts rather than reproduced locally.
- Dirty `.memory/` changes from hooks are bundled with the nearest related work commit, not committed as standalone routine work.
- When GitHub logs are unavailable, the fallback is narrower evidence from workflow files, snapshot artifacts, git history, and known contract tests, not local Gradle execution.

## Details

On 2026-05-31 the user clarified that even quick local `ktlintCheck`, `detekt`, Gradle build, and test tasks are forbidden for Ozero. Earlier sessions still allowed "fast static gates"; the corrected rule is stricter: local validation is read-only code inspection plus non-Gradle checks, while all executable validation goes through GitHub Actions.

This changes the debugging workflow. When `gh` or API access is unavailable, the agent must not compensate by running local tests. The fallback is to narrow failure risk through `ci.yml`, local git history, known memory articles, committed test contracts, and available CI artifacts or snapshots. The rule connects directly to [[concepts/github-actions-run-id-monitoring]] and [[concepts/ci-artifact-report-driven-debugging]] because terminal CI status and real artifacts become the primary evidence source.

On 2026-06-04 the user explicitly allowed local tests during a stubborn `dev` CI recovery cycle. That exception was useful for catching obvious compile, lint, unit, and JaCoCo failures before another push, but it did not replace [[concepts/ci-terminal-success-fresh-run-contract]]. A local green unit-test subset was not enough to push if local coverage verification still predicted a known remote red state.

The 2026-05-31 CI recovery showed the stricter fallback path in practice. With `gh` and the GitHub API unavailable, diagnosis moved to snapshot artifacts, workflow configuration, local git history, `.memory` articles, and code-level contract review. The important constraint is behavioral: lack of remote logs does not justify running local Gradle, lint, or tests in Ozero.

## Related Concepts
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/ci-workflow-discipline]]
- [[concepts/memory-commit-with-work-only]]
- [[connections/local-validation-vs-terminal-ci-proof-loop]]
- [[connections/multi-engine-stabilization-ci-review-loop]]

## Sources
- [[daily/2026-05-31]]: The user repeatedly prohibited local tests and later corrected the rule to ban local Gradle/lint/test checks entirely.
- [[daily/2026-05-31]]: The CI-debug sessions used artifacts, workflow analysis, memory, and static review when GitHub API or exact logs were unavailable.
- [[daily/2026-05-31]]: sessions 19:29 through 20:03 record the fallback from unavailable `gh` logs to workflow, snapshot artifacts, git history, and contract-focused review.
- [[daily/2026-06-04]]: Sessions 19:35, 21:17 and 21:35 record explicit permission for local lint/tests, local unit green but JaCoCo red, and the decision not to push a known-red coverage state.
