---
title: "Nubia ROM Permission Enforcement Quirks"
aliases: [nubia-redmagic-quirks, nubia-android-15]
tags: [android, vendor-rom, nubia, permissions, crash-diagnosis]
sources:
  - "daily/2026-04-29.md"
  - "daily/2026-04-30.md"
created: 2026-04-29
updated: 2026-04-30
---

# Nubia ROM Permission Enforcement Quirks

Nubia (and its gaming sub-brand RedMagic) Android ROMs may enforce permissions and system policies more strictly than stock AOSP. This was identified as a contributing factor during the investigation of a silent crash on a Nubia NX729J device running Android 15. The device exhibited a crash pattern not reproducible on other Android 15 devices, suggesting vendor-specific behavior.

## Key Points

- Nubia NX729J (RedMagic) running Android 15 exhibited a silent crash at app startup that did not reproduce on other devices
- Nubia ROM may enforce stricter permission checks during Activity/Service lifecycle phases
- The vendor library `libglnubia.so` has been implicated in prior crashes (v1.0.3 SIGSEGV in `nubia::Messager::timerLoop` when loading native libs from coroutine worker thread)
- Testing on real Nubia hardware is required — emulators and other devices cannot reproduce vendor-specific behavior
- The specific crash mechanism on Nubia remains unconfirmed pending diagnostic data collection (boot.log, adb logcat, dumpExitReasons)

## Details

Vendor ROM modifications are a known source of Android compatibility issues. Major OEMs (Samsung, Xiaomi, Huawei, Oppo, Nubia) modify the Android framework layer, adding custom permission managers, battery optimization, and process lifecycle policies that can break apps in ways not seen on AOSP or Pixel devices.

For Ozero VPN specifically, two Nubia-related issues have been documented:

1. **v1.0.3 SIGSEGV**: Loading `libhev-socks5-tunnel.so` from a coroutine worker thread triggered a crash in `libglnubia.so`'s `nubia::Messager::timerLoop`. The fix was to ensure `loadOnce()` runs only on the main thread, called from `OzeroVpnService.startVpn` before any coroutine dispatch. This is protected by `OzeroVpnServiceLifecycleTest`.

2. **v1.0.5 silent crash**: The VPN toggle click never reached `onConnectClick`, and no persistent log was written. Multiple hypotheses were evaluated (LaunchedEffect exception, Hilt DI graph failure, POST_NOTIFICATIONS, FGS specialUse), but the root cause remains unconfirmed. Nubia-specific permission enforcement is suspected as a contributing factor.

3. **v0.0.1 metered VPN throttle**: Nubia ROM aggressively throttles VPN connections marked as metered. Without `VpnService.Builder.setMetered(false)` (API Q+), throughput is unstable. ByeByeDPI calls `setMetered(false)` on Q+; Ozero initially omitted it. This was fix #8 in the v0.0.1 retag cycle.

The practical takeaway is that any change to the app startup path, native library loading, foreground service lifecycle, or VPN Builder configuration must be tested on real Nubia hardware before shipping.

## Related Concepts

- [[concepts/android-silent-crash-diagnosis]] - The methodology used to investigate the Nubia-specific silent crash
- [[concepts/hilt-di-native-library-failure]] - Native library loading issues may be compounded by Nubia ROM behavior
- [[concepts/compose-launchedeffect-crash-invisibility]] - The primary hypothesis for the silent crash on this device
- [[concepts/vpnservice-builder-traps]] - setMetered(false) is a Builder configuration trap specific to Nubia

## Sources

- [[daily/2026-04-29.md]] - Silent crash investigation on Nubia NX729J; v1.0.3 SIGSEGV history referenced; Nubia ROM strictness identified as factor requiring real-device testing
- [[daily/2026-04-30.md]] - setMetered(false) identified as fix #8 for Nubia metered VPN throttle during v0.0.1 retag cycle
