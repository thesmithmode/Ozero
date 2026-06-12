---
title: "ByeDPI UI Mode Already Owns Desync Feature Parity"
aliases: [byedpi-ui-mode-desync-parity, byedpi-settings-feature-audit, byedpi-desync-ui-fields]
tags: [byedpi, ui, settings, desync, audit]
sources:
  - "daily/2026-05-19.md"
created: 2026-06-12
updated: 2026-06-12
---

# ByeDPI UI Mode Already Owns Desync Feature Parity

Before adding new ByeDPI bypass controls, audit `ByeDpiUiSettings` and `ByeDpiUiArgsBuilder`: the v0.1.6 review found that the expected UI-mode desync features were already present. Missing traffic flow therefore should not be diagnosed as "no UI settings" unless the actual fields or generated args are proven absent.

## Key Points

- `ByeDpiUiSettings` already models TLS record split, split position, SNI split, HTTP/HTTPS desync, UDP desync, fake count, OOB, fake SNI, TTL/offset, and case-mutation controls.
- `ByeDpiUiArgsBuilder.buildArgsOnly` already renders the expected flags such as `-K{t,h}`, `-r<pos>+s`, `-Ku -a<count>`, `-M{h,d,r}`, and related desync values.
- `DEFAULT_BYEDPI_USE_UI_MODE=true`, so UI-mode fields are active by default unless CMD/winning args override them.
- Traffic failures with these features present should be investigated through pipeline parity, argv grammar, stale native state, or device trace.
- Tasks that request the same desync controls should be closed as duplicate only after code-level field and args-builder verification.

## Details

The 2026-05-19 release audit initially treated missing ByeDPI desync settings as a likely root cause of traffic failure. A later review showed that the required fields and args rendering had already been implemented in the UI-mode path. The correct conclusion was narrower: the UI-mode feature set was not the immediate missing layer.

This matters because ByeDPI has multiple override paths. `byedpiWinningArgs` or CMD mode can bypass UI-mode defaults, while invalid evolved args can still fail even when UI-mode is complete. Therefore an investigation must first establish which mode supplied the runtime argv, then compare the resulting argv against [[concepts/byedpi-argv-grammar-aware-validation]] and the reference pipeline in [[concepts/byedpi-hev-pipeline-upstream-parity]].

The concept complements [[concepts/byedpi-cmd-verbatim-pipeline]]: CMD mode must preserve user args verbatim, while UI mode must produce structured args from settings. Mixing these paths leads to misleading fixes such as changing defaults when runtime is actually using stored winning args.

## Related Concepts

- [[concepts/byedpi-argv-grammar-aware-validation]] - Ensures generated or evolved args preserve ByeDPI option grammar.
- [[concepts/byedpi-cmd-verbatim-pipeline]] - Defines when stored CMD/winning args override UI defaults.
- [[concepts/byedpi-hev-pipeline-upstream-parity]] - Covers HEV/YAML and IPv6 parity when args are not the root cause.

## Sources

- [[daily/2026-05-19.md]] - Session v0.1.6: audit of tasks #16-#21 found `ByeDpiUiSettings` and `ByeDpiUiArgsBuilder` already contain TLS split, HTTP/HTTPS, UDP, OOB, fake SNI, TTL/offset, and mixed-case controls; `DEFAULT_BYEDPI_USE_UI_MODE=true`; related tasks were closed as duplicates of existing implementation.
