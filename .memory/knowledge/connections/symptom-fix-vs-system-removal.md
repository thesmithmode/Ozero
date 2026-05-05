---
title: "Connection: Symptom Fix vs System Removal"
connects:
  - "concepts/urnetwork-sdk-integration"
  - "concepts/urnetwork-networkspace-init"
sources:
  - "daily/2026-05-02.md"
created: 2026-05-02
updated: 2026-05-02
---

# Connection: Symptom Fix vs System Removal

## The Connection

The URnetwork consent system in Ozero was a permission gate that required users to accept terms before the engine could start. When it was discovered that consent was only auto-granted if the user opened the Settings screen (a UI-dependent initialization path), the initial instinct was to fix the auto-grant mechanism. An advisor call revealed this was symptom-patching — the correct fix was removing the consent system entirely, since URnetwork's business model does not require user consent for the P2P engine in Ozero's context.

## Key Insight

The non-obvious relationship is between two problem-solving approaches that produce the same immediate behavior (engine starts without manual consent) but have drastically different system effects:

1. **Auto-grant fix** (symptom): Add initialization code to ensure consent is granted before engine start, regardless of whether Settings was opened. Preserves `UrnetworkConsentStore`, `UrnetworkModule` consent parameters, and all consent-checking logic. Adds complexity.

2. **System removal** (root cause): Delete `UrnetworkConsentStore`, remove consent parameters from `UrnetworkModule`, eliminate consent checks from `EngineUrnetwork`. Simplifies the DI graph, removes an entire code path, and eliminates the class of bugs where consent state is incorrect.

The advisor intervention was critical — it caught the pattern before the auto-grant workaround was implemented. The lesson: when a subsystem exists only because it was copied from a reference implementation (URnetwork's own app requires consent for their SaaS model), and the integrating app has no business need for it, removing the subsystem is always preferable to patching it.

## Evidence

From the v0.0.2-5 development cycle:

- **Symptom**: URnetwork engine failed with "consent not granted" unless user had opened Settings screen
- **Initial approach**: Auto-grant consent in `EngineUrnetwork.start()` before SDK initialization
- **Advisor recommendation**: Delete consent system entirely — Ozero bundles URnetwork as an engine, not as a standalone product requiring user agreement
- **Result**: `UrnetworkConsentStore`, `UrnetworkModule` consent parameters, and all consent-checking logic removed. DI graph simplified. The bug class "consent not properly initialized" was eliminated rather than fixed.

This pattern reinforces the root cause discipline documented in `CLAUDE.md`: "Фиксить корень, не симптом. 3 фикса подряд не помогли → пересмотр гипотезы."

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] - The engine integration where the consent system was removed
- [[concepts/urnetwork-networkspace-init]] - The next-level initialization problem discovered after consent was removed
- [[connections/byedpi-reference-parity]] - Similar pattern: deviating from reference causes bugs, but blindly copying reference (consent system) also causes bugs when context differs
