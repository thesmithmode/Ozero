---
title: Public repo secret and insecure asset boundary
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# Public repo secret and insecure asset boundary

## Summary
Secrets and insecure proxy definitions must not be shipped as public repository assets, and backup encryption cannot rely on a static key embedded in code.

## Key Points
- A static backup key in source is public to every APK or repository reader.
- Backups containing WARP, FPTN, or URnetwork data need passphrase-based or Keystore-backed protection.
- Bootstrap assets must not contain live proxy credentials.
- Insecure proxy definitions should not be enabled by default.
- This extends [[concepts/core-backup-module]] and [[concepts/prod-log-infra-redaction]].

## Details
The 2026-05-31 security review found that backup encryption relied on a static key, making encrypted backups effectively decryptable by anyone who can inspect the APK or public repository. For backups that include VPN configuration or account-related engine data, static-key encryption is not a real protection boundary.

The same review found bootstrap assets containing live proxy credentials and insecure definitions. In a public repository, these assets are indexed permanently and may leak access details or normalize unsafe defaults. The durable fix is to remove live credentials, require user-provided secrets or secure storage, and avoid insecure profiles by default.

## Related Concepts
- [[concepts/core-backup-module]]
- [[concepts/prod-log-infra-redaction]]
- [[concepts/private-subscription-profile-sanitized-evidence]]
- [[concepts/engine-config-private-key-plaintext]]

## Sources
- [[daily/2026-05-31]]: session 20:48 records that backup encryption is based on a static key.
- [[daily/2026-05-31]]: session 20:48 records that bootstrap assets contain live proxy credentials and insecure definitions.
- [[daily/2026-05-31]]: session 20:49 records the remediation priority: passphrase/Keystore backup encryption and removal of live or insecure proxy credentials.
