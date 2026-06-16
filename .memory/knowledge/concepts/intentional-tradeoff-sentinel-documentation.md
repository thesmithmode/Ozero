---
title: Intentional Tradeoff Sentinel Documentation
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# Intentional Tradeoff Sentinel Documentation

## Key Points
- Intentional architecture tradeoffs that look like bugs should be documented near the code with a short sentinel explanation.
- The backup static key is a product contract for one-click cross-device restore, not a security bug to change without a product decision.
- Repeat reviews should distinguish live credentials or insecure bootstrap assets from an intentional backup restore contract.
- Project rules should reflect validation limits clearly; in Ozero local Gradle, lint, and test tasks are fully forbidden.

## Details

The 2026-05-31 review initially classified the backup static key as a security concern. The user clarified that this is an intentional architecture decision: Ozero backup/restore must work in one click without passphrase or Keystore coupling, even though backups can contain sensitive engine credentials. The right action was to preserve the contract and document the intent so future reviewers do not repeatedly "fix" it.

This does not weaken the separate rule that public assets must not contain live proxy credentials or insecure defaults. The same review kept those issues in scope while excluding backup encryption redesign. The distinction is important: intentional product tradeoffs need sentinel documentation, while accidental sensitive assets remain bugs.

The session also tightened validation rules: even quick local Gradle, lint, and test tasks are forbidden in this repository. That rule must be reflected in workflow expectations and final validation claims, especially when changes touch CI or coverage contracts such as [[concepts/ci-coverage-gate-artifact-trust-contract]].

## Related Concepts
- [[concepts/backup-one-click-restore-contract]]
- [[concepts/public-repo-secret-and-insecure-asset-boundary]]
- [[concepts/ci-coverage-gate-artifact-trust-contract]]
- [[concepts/code-quality-review-proof-standard]]

## Sources
- [[daily/2026-05-31]]: Session 21:23 records the user clarification that `BackupCipher` static key is intentional and should be documented in code.
- [[daily/2026-05-31]]: Sessions 20:48 and 20:57 separate backup static-key policy from live proxy credentials and CI coverage trust issues.
