---
title: URnetwork Consent System Deletion
sources:
  - daily/2026-05-02.md
created: 2026-06-12
updated: 2026-06-12
---
# URnetwork Consent System Deletion

## Key Points
- URnetwork consent auto-grant was a workaround, not a product requirement.
- The bug appeared because consent was auto-granted only after the user opened the settings screen.
- Engine startup could fail with `consent not granted` when settings were never opened.
- The chosen fix was deleting `UrnetworkModule`, `UrnetworkConsentStore`, and consent methods from the DI graph.
- Removing the gate simplified `EngineUrnetwork` and avoided hidden UI-dependent startup state.

## Details

The 2026-05-02 session found that URnetwork startup depended on a consent state that was not actually meaningful for the product. Auto-granting consent only from the settings screen meant the engine behaved differently depending on whether the user had visited UI settings before starting the VPN. That made startup correctness depend on incidental UI navigation rather than engine configuration.

The durable lesson was to remove unnecessary permission systems instead of patching around them. Auto-grant would have preserved a dead gate and kept future failures possible in other paths. Deleting the consent store, module wiring, and consent methods made engine startup depend on the real SDK and configuration contracts instead of a synthetic flag.

## Related Concepts
- [[concepts/urnetwork-sdk-integration]]
- [[concepts/engine-runtime-provider-composition-root-boundary]]
- [[concepts/hilt-cross-process-injection]]
- [[connections/engine-startup-status-authority-boundary]]

## Sources
- [[daily/2026-05-02]]: Session 10:15 records that the advisor stopped an auto-grant workaround and the root fix was deleting the URnetwork consent system.
- [[daily/2026-05-02]]: Session 10:15 records that consent was auto-granted only when the user opened settings, causing engine startup to fail with `consent not granted`.
