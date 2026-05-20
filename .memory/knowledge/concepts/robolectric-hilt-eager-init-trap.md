---
title: "Robolectric + @HiltAndroidApp: Eager Field Init NPE"
aliases: [robolectric-hilt-npe, eager-init-npe, hilt-robolectric-trap]
tags: [testing, android, hilt, robolectric, gotcha]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# Robolectric + @HiltAndroidApp: Eager Field Init NPE

Robolectric loads the real `Application` class from `AndroidManifest.xml`. If it's annotated `@HiltAndroidApp`, Hilt initializes the full DI graph including all `@Inject` services. Services with eager field initialization (e.g., `private val wrapper = MtgWrapper(context.applicationInfo.nativeLibraryDir)`) crash with NPE because Robolectric doesn't fully populate `applicationInfo.nativeLibraryDir`.

## Key Points

- Robolectric without `@Config(application = Application::class)` loads the real Application from manifest — if `@HiltAndroidApp`, Hilt initializes
- Hilt creates all `@Inject` services during Application init — any eager field accessing Robolectric-unpopulated fields → NPE
- Fix 1 (service): `by lazy { ... }` for fields depending on runtime context — defers init until actual usage
- Fix 2 (test): `@Config(application = Application::class)` bypasses OzeroApp/Hilt entirely
- Both fixes should be applied: lazy in service (defensive) + @Config in test (isolation)

## Details

### The Mechanism

1. `UpdateInstallResultReceiverTest` runs under Robolectric
2. Robolectric reads `AndroidManifest.xml` → finds `OzeroApp` as `<application>`
3. `OzeroApp` has `@HiltAndroidApp` → Hilt initializes DI graph
4. DI graph includes `TelegramProxyService` with `@Inject`
5. `TelegramProxyService` body: `private val wrapper = MtgWrapper(context.applicationInfo.nativeLibraryDir)`
6. Robolectric's `context.applicationInfo.nativeLibraryDir` = null → NPE at line 23

### Why Lazy Fixes It

`private val wrapper by lazy { MtgWrapper(context.applicationInfo.nativeLibraryDir) }` defers the field initialization until `wrapper` is first accessed (in `start()` or `generateSecret()`). During Hilt DI init, the field is just a `Lazy<MtgWrapper>` instance — no native library dir access.

### Cascade Risk

Adding any `@Inject` field to `Application` that transitively accesses Robolectric-unpopulated fields will break ALL Robolectric tests in the project. The `TelegramProxyCoordinator` `@Inject` in `OzeroApp.onCreate` triggered 9 test failures across `UpdateInstallResultReceiverTest`.

## Related Concepts

- [[concepts/hilt-di-native-library-failure]] - Related: Hilt DI graph breaks when System.loadLibrary fails; both are Hilt initialization traps
- [[concepts/amneziawg-relinker-loading-trap]] - AbstractBackend ctor loads native lib; similar eager-init-in-constructor issue
- [[concepts/manual-di-design]] - Complementary at a different layer: eager init in `OzeroApp.onCreate` via `VpnServiceLocator` for production crash visibility. That is service-locator init, NOT `@Inject` field init — the lazy pattern documented here applies to fields inside `@Inject` services.

## Sources

- [[daily/2026-05-14.md]] - Session 15:xx: 9x NPE in UpdateInstallResultReceiverTest from TelegramProxyService.kt:23; Robolectric loads OzeroApp → Hilt creates TelegramProxyService → nativeLibraryDir=null
- [[daily/2026-05-14.md]] - Session 17:xx: root fix = `by lazy {}` in TelegramProxyService; `@Config(application = Application::class)` kept as test isolation
