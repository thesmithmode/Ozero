---
title: TCP Probe Test Local Closed Port Contract
sources:
  - [[daily/2026-06-04]]
created: 2026-06-04
updated: 2026-06-04
---
# TCP Probe Test Local Closed Port Contract

## Key Points
- TCP failure tests should not rely on a private external IP being unreachable.
- A supposedly blackholed address such as `10.255.255.1` can answer in the current local network.
- Use a controlled closed localhost port to produce deterministic connection failure.
- The test should assert the probe contract, not incidental routing behavior of the developer or CI network.

## Details

During the 2026-06-04 CI recovery, `TcpProbeTest` was found fragile because `10.255.255.1`, previously treated as a non-answering private address, returned `Ok` in the local network. That made the test dependent on ambient routing rather than the TCP probe failure contract.

The stable pattern is to allocate or identify a localhost port with no listener and use that as the failure target. This makes the negative path independent of external network topology and keeps the test focused on controlled connection refusal or timeout handling.

## Related Concepts
- [[concepts/regression-test-bounded-waits]]
- [[concepts/viewmodel-polling-runtest-trap]]
- [[concepts/ci-current-run-batch-failure-triage]]
- [[concepts/local-gradle-validation-ban-ci-only]]

## Sources
- [[daily/2026-06-04]]: sessions 21:17 and 21:35 record that `10.255.255.1` unexpectedly answered locally and was replaced with a guaranteed closed local port.
