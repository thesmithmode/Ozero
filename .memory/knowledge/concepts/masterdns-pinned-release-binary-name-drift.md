---
title: MasterDNS deploy must handle upstream binary name drift through pinned releases
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---

# MasterDNS deploy must handle upstream binary name drift through pinned releases

## Key Points
- MasterDNS install failures after a failed attempt can come from build-time `bin_missing`, not leftover Docker state.
- The Dockerfile must not assume the old `masterdnsvpn-server` binary name when upstream release assets use `MasterDnsVPN_Server_Linux*_v*`.
- Deploy should use a pinned upstream release asset or image, with fallback to the upstream installer only when intended.
- Cleanup must be safe and avoid deleting persistent key volume state such as `masterdns-key`.
- Diagnostics should preserve structured `bin_missing` evidence and user-facing localized error mapping.

## Details

The 2026-05-31 MasterDNS stream separated real cleanup issues from upstream packaging drift. Preflight could pass Docker and UDP/53 checks while image build still failed because the expected server binary name no longer matched upstream release output. Treating that as generic post-failure garbage would misdirect the fix.

The chosen deploy contract is to pin a known upstream release and handle the current binary naming pattern explicitly. Cleanup remains conservative: remove failed containers/images as needed, but preserve key volume data. Additional retry around key reading and structured diagnostics make the failure actionable without leaking host-specific details.

## Related Concepts
- [[concepts/masterdns-docker-build-run-proof-contract]]
- [[concepts/masterdns-amnezia-dns-removal-success-contract]]
- [[concepts/masterdns-amnezia-dns-running-udp-contract]]
- [[concepts/masterdns-deploy-hardening]]

## Sources
- [[daily/2026-05-31]]: sessions 11:39, 11:46, 12:13, and 12:27 describe `bin_missing`, upstream binary name drift, pinned release deploy, safe cleanup, retry, and localized diagnostics.
