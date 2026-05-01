---
title: "Connection: ByeByeDPI Reference Parity as Bug Prevention"
connects:
  - "concepts/v001-dpi-bypass-fix-chain"
  - "concepts/byedpi-args-parsing"
  - "concepts/tun-mtu-dual-layer"
  - "concepts/vpnservice-builder-traps"
sources:
  - "daily/2026-04-30.md"
created: 2026-04-30
updated: 2026-04-30
---

# Connection: ByeByeDPI Reference Parity as Bug Prevention

## The Connection

All nine fixes in the v0.0.1 release cycle share a common pattern: each bug was caused by Ozero's VPN configuration diverging from the ByeByeDPI/ByeDPIAndroid reference implementation. The fixes restored parity with upstream. This reveals that ByeByeDPI serves not just as a feature reference but as a correctness oracle for the entire VPN integration layer.

## Key Insight

The non-obvious relationship is that seemingly independent issues — argument parsing (`argv[0]`, `-Ku`), Builder configuration (`setMtu`, `setBlocking`, IPv6 routes, `setMetered`), native fd handling (`fd.dup()`), service lifecycle (stop order), and DNS configuration — all stem from the same root mistake: implementing VPN tunnel setup from Android documentation and first principles rather than matching the proven ByeByeDPI configuration exactly.

Android VPN documentation describes what each API does in isolation but does not document the interactions between `VpnService.Builder`, `libhev-socks5-tunnel`, and `byedpi`'s `getopt_long` parser. ByeByeDPI's source code is the only specification that captures these interactions correctly. Any deviation from it must be treated as a hypothesis requiring device testing, not an improvement.

## Evidence

The nine v0.0.1 fixes, grouped by divergence type:

- **Builder API divergence**: `setBlocking(true)` (not called in ByeByeDPI), `setMtu(8500)` (not called), unconditional IPv6 routes (conditional in ByeByeDPI), missing `setMetered(false)` (called in ByeByeDPI)
- **Native interop divergence**: `fd.dup()` (ByeByeDPI passes raw fd), stop order libhev→byedpi (ByeByeDPI stops byedpi first)
- **Args divergence**: missing `argv[0]="ciadpi"` (ByeByeDPI prepends it), `-Ku` in defaults (ByeByeDPI defaults to `-Kt,h`)
- **DNS divergence**: CGNAT 100.64.0.1 (ByeByeDPI uses public DNS)

Every fix was "make it match ByeByeDPI." Zero fixes required novel approaches.

## Related Concepts

- [[concepts/v001-dpi-bypass-fix-chain]] - The complete 9-fix chain demonstrating the pattern
- [[concepts/byedpi-args-parsing]] - Args parsing traps discovered by reading ByeByeDPI source
- [[concepts/tun-mtu-dual-layer]] - MTU configuration matching ByeByeDPI's omission of setMtu()
- [[concepts/vpnservice-builder-traps]] - Builder API traps where Ozero diverged from ByeByeDPI
