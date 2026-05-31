---
title: ByeDPI argv grammar-aware validation
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# ByeDPI argv grammar-aware validation

## Key Points
- ByeDPI argv validation must preserve option/value pairs, including detached short-option values such as `-s 25+s`.
- Manual allowlist validation can reduce native crash risk but can also reject valid shipped strategies if it is not grammar-aware.
- Quoted SNI normalization belongs only to the `-n` value path and must not allow values that begin with `-`.
- Strategy evolution should mutate option blocks, not arbitrary tokens, to avoid invalid argv churn.

## Details

On 2026-05-31, Codex PR review found that `ByeDpiArgvValidator` rejected valid short options with detached values, such as `-s 25+s`, and later found a separate quoted SNI issue. The fixes were treated as real defects because shipped and generated strategies can contain detached option values, and rejecting them turns working strategies into `startFailed` candidates.

The deeper rule is that ByeDPI strategy scan cannot rely on token-level genetic operations plus a broad allowlist. The grammar unit is an option block: flag plus its required or optional value. Mutation, crossover, reduction, and validation should keep those blocks intact, otherwise the evolution engine may appear safe while still toppling into invalid native starts or rejecting real user strategies.

## Related Concepts
- [[concepts/byedpi-args-parsing]]
- [[concepts/byedpi-strategy-scan-isolated-structured-argv]]
- [[concepts/byedpi-cmd-verbatim-pipeline]]
- [[concepts/byedpi-youtube-quic-probe-domain-contract]]

## Sources
- [[daily/2026-05-31]]: Session 15:03 records that a manual argv allowlist was not enough because GA still generated non-structural argv.
- [[daily/2026-05-31]]: Session 16:19 records Codex review finding that detached `-s 25+s` was rejected.
- [[daily/2026-05-31]]: Session 18:04 records the follow-up quoted SNI defect and the decision to normalize only `-n` values.
