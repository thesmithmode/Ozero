---
title: binaries.lock.yaml may intentionally retain real upstream URLs
sources:
  - daily/2026-05-27.md
created: 2026-05-28
updated: 2026-05-28
---
# Binaries Lock Real URL Exception

## Key Points
- Debranding should not blindly rewrite binaries.lock.yaml URLs.
- Lockfile URLs can be functional download coordinates for native .so artifacts.
- Replacing real upstream owner paths can break binary regeneration or CI verification.
- Treat lockfile URL changes as dependency changes, not text cleanup.

## Details

In the 2026-05-27 debranding session, binaries.lock.yaml was intentionally left unchanged even though it still contained owner-specific URL paths. Those entries were real download URLs for native .so files, not branding text.

The general rule is to distinguish identity metadata from artifact coordinates. Docker labels, test fixtures, build constants, and visible repo metadata can be sanitized, but lockfile coordinates must keep the exact upstream source unless the binary source itself is migrated and verified.

## Related Concepts
- [[concepts/native-binary-auto-update-pipeline]]
- [[concepts/gitignore-jnilibs-conflict]]
- [[concepts/repo-debranding-ci-owner-injection]]

## Sources
- [[daily/2026-05-27.md]]: Session 16:50 recorded the decision not to edit binaries.lock.yaml because the URLs are working .so download URLs.
