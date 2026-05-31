---
title: sing-box VLESS early data string contract
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# sing-box VLESS early data string contract

## Summary
sing-box VLESS import must preserve string-typed transport parameters and map `ed` and `eh` to the correct JSON fields, because libbox strict decoding rejects numeric JSON where a string is expected.

## Key Points
- `ed` in VLESS URLs is max early data, not `early_data_header_name`.
- `eh` is the header-name parameter and must remain a JSON string even when it looks numeric.
- Numeric-looking subscription parameters must not be blindly converted to JSON numbers.
- Server testing should use bounded concurrency and per-profile state so UI feedback maps to the tested profile.
- This contract is related to [[concepts/singbox-private-subscription-chain-validation]] and [[concepts/singbox-routed-probe-readiness-latency-contract]].

## Details
On 2026-05-31 a sing-box crash was traced to JSON generation that serialized a numeric-looking value for `early_data_header_name` as a number. libbox failed with a strict unmarshal error because that field expects a string. The fix direction was to treat `ed` as max early data, `eh` as header name, and keep header names string-typed through import and config generation.

The same session connected this parser bug with UX problems around server testing: tests should be bounded to at most 10 concurrent checks and tracked by concrete profile id. That prevents unbounded probe pressure and lets the UI show exactly which profiles are being tested rather than a coarse global busy state.

## Related Concepts
- [[concepts/singbox-private-subscription-chain-validation]]
- [[concepts/singbox-routed-probe-readiness-latency-contract]]
- [[concepts/singbox-karing-json-import-parity]]
- [[concepts/singbox-autochain-validator-parity]]

## Sources
- [[daily/2026-05-31]]: session 19:02 identified the crash where `early_data_header_name=1` became a JSON number and libbox rejected it.
- [[daily/2026-05-31]]: session 19:17 records the corrected mapping: `ed` as max early data and `eh` as header name.
- [[daily/2026-05-31]]: session 19:02 defines the bounded server-testing queue and per-profile testing state requirement.
