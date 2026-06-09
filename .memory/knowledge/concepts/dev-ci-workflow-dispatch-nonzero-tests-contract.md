---
title: Dev CI workflow_dispatch and nonzero tests contract
sources:
  - daily/2026-05-30.md
created: 2026-05-31
updated: 2026-06-09
---
# Dev CI workflow_dispatch and nonzero tests contract
## Summary
Ozero `dev` CI proof requires a terminal workflow run on the intended SHA and artifact evidence that touched modules discovered nonzero tests.

## Key Points
- Ozero `dev` push does not necessarily run the main CI workflow when workflow triggers ignore `dev`.
- Main CI for a dev SHA may need explicit `workflow_dispatch`.
- A green workflow is false confidence when a changed module reports `0` tests.
- Artifact or XML audit is required to verify that relevant module tests actually executed.
- The authoritative CI signal must be anchored to the intended SHA, not inferred from a nearby branch or PR status.
- This strengthens [[concepts/ci-module-test-coverage-gap]] and [[concepts/junit-platform-silent-skip]].

## Details

The 2026-05-30 CI cycle exposed two separate false-green vectors. First, the repository workflow configuration meant that a push to `dev` did not run the main `.github/workflows/ci.yml` path in the expected way; the main CI for a dev SHA was triggered through `workflow_dispatch`. Second, after an apparently green dev CI, artifact audit showed that `singbox-process` had run zero tests even though production code in that module changed.

The acceptance rule is therefore stronger than workflow conclusion. For touched modules, especially engine or process modules, CI evidence must include N>0 test execution or an explicit sentinel that makes the module visible to the runner. Logs can say `BUILD SUCCESSFUL` while hiding absent tests, so test report artifacts and XML outputs are part of the proof.

This rule is especially important before merging `dev` to `main` or publishing release artifacts. A green status without module-level test evidence is delivery evidence only, not behavioral proof.

The later compile of the same daily log clarified the practical acceptance rule: the trusted signal is a terminal workflow run for the exact target SHA plus test-report evidence that touched modules discovered tests. If either the workflow trigger or module test discovery is ambiguous, the gate needs a fresh explicit run and artifact audit.

## Related Concepts
- [[concepts/ci-module-test-coverage-gap]]
- [[concepts/ci-engine-module-missing-tests]]
- [[concepts/junit-platform-silent-skip]]
- [[connections/release-ci-green-vs-runtime-engine-proof]]

## Sources
- [[daily/2026-05-30]]: After the first green dev CI, artifact audit found `singbox-process` had zero tests despite production-code changes.
- [[daily/2026-05-30]]: A sentinel test was added and a new CI run on the new SHA was required.
- [[daily/2026-05-30]]: The session noted that `ci.yml` ignored `dev`, so the main CI for a dev SHA was run via `workflow_dispatch`.
- [[daily/2026-05-30]]: The lesson recorded that GitHub Actions logs can show successful build output without proving N tests.
- [[daily/2026-05-30]]: The final acceptance target was a workflow_dispatch run on SHA `e9709942` after adding a `singbox-process` sentinel.
