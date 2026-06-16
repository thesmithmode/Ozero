---
title: "Singbox UI and CI Diagnostic Feedback Loop"
aliases: [singbox-ui-ci-loop, singbox-crash-ping-ci-loop, singbox-diagnostic-loop]
tags: [singbox, android, ci, diagnostics, ui]
sources:
  - "daily/2026-05-26.md"
created: 2026-06-13
updated: 2026-06-13
---

# Singbox UI and CI Diagnostic Feedback Loop

Singbox regressions on 2026-05-26 showed that native crash evidence, parser compatibility fixes, ViewModel cancellation, and CI/code-review hygiene form one feedback loop. Treating these as unrelated UI or CI tasks misses the cross-layer cause chain: bad subscription config can crash `:engine_singbox`, crash recovery can surface as broken reconnect/UI state, and reviewers can misclassify correct coroutine patterns without runtime context.

## Key Points

- Native crashes need tombstones before assigning root cause; `DeadObjectException` is only the Binder symptom of `:engine_singbox` death.
- Subscription parser compatibility fixes, such as removing `dns` outbounds and filtering unsupported `splithttp`, should happen before runtime startup.
- Ping UI correctness depends on both protocol-neutral `AbstractBean` deserialization and cancellable ViewModel jobs.
- Code-review findings from subagents must be rechecked against Compose and coroutine semantics before applying them.
- CI troubleshooting must anchor to the current branch and concrete run ID, because stale branches and stale `gh run list` output create false diagnostics.

## Details

The same daily log combined three classes of evidence: device/native failure signals, UI state behavior, and CI workflow traps. The singbox crash investigation found SIGABRT/SIGSEGV in `:engine_singbox`, unsupported `splithttp`, deprecated `dns` outbound usage, and later `checkConfig passed` followed by `startWithConfig` native crash. These are not solved by UI changes; they require subscription normalization plus tombstone-backed native diagnosis.

The UI work around ping and cancellation had a separate but related contract. Latency probing must deserialize through `AbstractBean` so all protocols have usable `serverAddress`, `serverPort`, and `probeLatencyMs`; the ViewModel must keep a `pingJob` reference so the visible "Cancel" action can cancel the active operation. Subagent review produced false positives around `isPinging.clear()` and `LaunchedEffect(key)`, showing that code-review output is only evidence after local semantic verification.

CI and branch hygiene connect the loop. The wrong-branch incident on `win11` and the `gh run list --limit 1` stale-run race both show that diagnostics can be invalid even before code is inspected. For singbox crash fixes, the reliable cycle is: confirm branch, collect concrete logs/tombstones, fix parser/runtime owner layer, preserve UI cancellation semantics, then validate through the intended CI run.

## Related Concepts

- [[concepts/singbox-crash-tombstone-diagnosis]] - Native crash evidence needed before diagnosing `:engine_singbox` failures.
- [[concepts/singbox-dns-outbound-deprecated]] - Parser-level compatibility fix for sing-box 1.13.0 configs.
- [[concepts/singbox-ping-abstractbean-deserialization]] - Protocol-neutral ping data extraction for server latency UI.
- [[concepts/pingJob-viewmodel-cancellation]] - ViewModel-owned cancellation contract for ping operations.
- [[concepts/git-active-branch-discipline]] - Branch verification needed before autonomous diagnostics.
- [[concepts/gh-run-list-watcher-race]] - CI run monitoring must avoid stale latest-run assumptions.

## Sources

- [[daily/2026-05-26.md]] - Session 13:59 recorded the tombstone pull path and ping UI/ViewModel decisions.
- [[daily/2026-05-26.md]] - Session 16:22 accepted only the null error-message fallback after personally rejecting two subagent false positives.
- [[daily/2026-05-26.md]] - Session 16:52 identified detekt threshold semantics and the stale `gh run list --limit 1` race.
- [[daily/2026-05-26.md]] - Session 19:44 tied singbox SIGABRTs to `dns` outbound deprecation, unsupported `splithttp`, and wrong-branch work on `win11`.
- [[daily/2026-05-26.md]] - Session 21:41 observed `DeadObjectException` and native crash in `libbox.startWithConfig` after `checkConfig passed`.
