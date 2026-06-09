---
title: Backup static key one-click restore contract
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-06-09
---
# Backup static key one-click restore contract

## Key Points
- Ozero intentionally uses a static backup key for one-click cross-device restore without account/server dependency.
- This is an architecture decision, not a bug to auto-fix.
- Security work must focus on warning users, avoiding repo/APK live credentials, and not weakening the backup UX silently.
- Bootstrap assets must not contain live proxy credentials or insecure definitions by default.
- Intentional backup tradeoffs should have code-level sentinel documentation so future reviews do not reclassify them as accidental leaks.

## Details

The 2026-05-31 review initially flagged backup encryption because the key is derived from a static value in source. The product decision is different: backup must support one-click cross-device restore without asking the user for an account, server, passphrase, or Keystore-bound device secret. Do not replace this with passphrase/Keystore flow unless the product requirement changes explicitly.

The same review also found that bootstrap assets contained live proxy credentials and insecure definitions. That remains a real public-repo/APK boundary bug: bundled assets must not ship live proxy credentials or insecure profiles.

The durable review lesson is documentation placement. A static key looks like a high-severity security defect unless the code itself marks the one-click restore constraint. A short sentinel comment near the backup cipher is acceptable here because it prevents future cleanup passes from replacing an intentional product contract with a device-bound or password-bound flow.

## Related Concepts
- [[concepts/core-backup-module]]
- [[concepts/engine-config-private-key-plaintext]]
- [[concepts/private-subscription-profile-sanitized-evidence]]
- [[concepts/prod-log-infra-redaction]]
- [[concepts/intentional-tradeoff-sentinel-documentation]]

## Sources
- [[daily/2026-05-31]]: User clarified that the static backup key is intentional for one-click restore and must not be changed as part of this bugfix pass.
- [[daily/2026-05-31]]: Session 20:48 records live proxy credentials and insecure definitions in bootstrap assets as security risks.
- [[daily/2026-05-31]]: Session 21:23 records the decision to document the static-key tradeoff directly in backup code.
