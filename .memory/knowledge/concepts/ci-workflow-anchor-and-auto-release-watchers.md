---
title: CI Workflow Anchor and Auto Release Watchers
sources:
  - daily/2026-05-02.md
created: 2026-06-12
updated: 2026-06-12
---
# CI Workflow Anchor and Auto Release Watchers

## Key Points
- CI watchers must anchor to the intended workflow, run, or commit instead of accepting any green workflow.
- `Gradle Wrapper Validation` can look green while the real `CI` workflow is still pending or red.
- Auto-tagging can be delegated to a watcher only after the target `CI` run reaches terminal success.
- A failed pre-release should be deleted and recreated from fixed `dev` rather than patched in place.
- Release automation must still preserve the final APK build check.

## Details

The v0.0.2-5 cycle showed that a watcher can produce false confidence if it watches the wrong GitHub Actions workflow. Without `--workflow "CI"`, a lightweight validation workflow may finish successfully while the actual build, style, test, or release-relevant workflow still has not proven the commit. The durable rule is to bind the watcher to the intended workflow and commit/run identity.

The release loop also established a constrained auto-release pattern. After the user allowed it, the watcher could tag `v0.0.2-5` automatically when `CI` turned green. This did not remove the need to verify release.yml and APK output; it only automated the tag creation after the correct terminal CI signal.

## Related Concepts
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/ci-terminal-success-fresh-run-contract]]
- [[concepts/release-process]]
- [[concepts/github-release-asset-name-verification]]

## Sources
- [[daily/2026-05-02]]: Session 11:40 records the lesson to use `--workflow "CI"` explicitly because `Gradle Wrapper Validation` can create a false green signal.
- [[daily/2026-05-02]]: Session 13:20 records the watcher that auto-tags `v0.0.2-5` only after CI success and then relies on release.yml for APK build.
