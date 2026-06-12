---
title: WARP Mirror Security and Config Backlog
sources:
  - daily/2026-05-02.md
created: 2026-06-12
updated: 2026-06-12
---
# WARP Mirror Security and Config Backlog

## Key Points
- Hardcoded WARP generator mirrors cannot be updated without a new APK.
- Missing certificate pinning is a security concern for censorship-heavy environments with hostile network middleboxes.
- Mirror health and API parsing are separate problems: a live mirror can still look dead if the client parses the response incorrectly.
- The immediate release fix was API contract parsing; pinning and externalized mirror config stayed backlog.
- WARP key warning UI was deferred because it did not block the v0.0.2-5 release.

## Details

The review of WARP auto-config separated operational failure from security hardening. The immediate runtime failure came from wrong API usage: Ozero expected raw WireGuard INI while the mirror returned JSON with `content.configBase64`. Once that contract was fixed, the mirror pool was not simply "dead"; earlier read timeouts were secondary to parsing and timeout assumptions.

The remaining backlog is still important. A static Kotlin list of mirrors makes response to mirror blocking slow because users need a new APK. Lack of certificate pinning leaves room for MITM or trust-store injection attacks, especially for the target audience under network censorship. Those issues were tracked as audit items rather than mixed into the release-critical parsing fix.

## Related Concepts
- [[concepts/warp-config-generator-api]]
- [[concepts/warp-pretunnel-endpoint-doh-parity]]
- [[concepts/public-repo-secret-and-insecure-asset-boundary]]
- [[connections/release-engine-fix-contract-vs-timeout]]

## Sources
- [[daily/2026-05-02]]: Session 11:03 records security review findings for hardcoded WARP mirrors and missing certificate pinning.
- [[daily/2026-05-02]]: Session 12:26 records that the real WARP root cause was JSON parsing, missing endpoint body, and too-short timeout; pinning and warning UI were deferred.
