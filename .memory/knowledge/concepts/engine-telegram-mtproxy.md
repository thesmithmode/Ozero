---
title: "MTProxy Subprocess Engine Pattern (engine-telegram)"
aliases: [engine-telegram, mtproxy-subprocess, telegram-proxy-coordinator, mtg-wrapper]
tags: [architecture, telegram, mtproxy, subprocess, engine, native]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# MTProxy Subprocess Engine Pattern (engine-telegram)

`engine-telegram` is a side-car subprocess module, NOT a VPN routing engine. It does not implement the `Engine` interface, is not registered via `@IntoSet`, and does not participate in the engine chain. Instead, it launches `libmtg.so` (a prebuilt Go binary) as a child process and routes its upstream through whichever VPN engine is currently active.

## Key Points

- `libmtg.so` = prebuilt Go binary in `jniLibs/<abi>/`, started via `ProcessBuilder` — NOT `System.loadLibrary`
- `TelegramProxyCoordinator`: observes `combine(tunnelState, configStore.config())` → selects upstream → calls `MtgWrapper.start/stop`
- WARP routing (`socksPort == 0`): subprocess inherits UID → traffic through TUN automatically (`excludeSelf=false`)
- SOCKS routing: pass `--socks5-proxy-url socks5://127.0.0.1:<port>` to mtg process
- `MtgWrapper` uses `by lazy {}` for `nativeLibraryDir` to avoid Robolectric NPE
- `binaries.lock.yaml` tracks prebuilt binary versions; `regen_lock.py` downloads `.so` + `manifest.yaml` to regenerate the lock
- `coordinatorScope = CoroutineScope(dispatcher + SupervisorJob())` — separate from `testScope` to avoid `UncompletedCoroutinesError`

## Details

### Architecture: Side-Car, Not Engine

The MTProxy binary (`mtg`) is a standalone Go program that acts as a Telegram MTProto proxy. It cannot be linked as a `.so` library in the traditional sense — it runs as a separate process. The module `engine-telegram` therefore:

- Does NOT implement `Engine` / `EnginePlugin`
- Is NOT registered with `@IntoSet` alongside Xray, Hy2, ByeDpi, etc.
- Runs concurrently with the active VPN engine, not instead of it

This means `TelegramProxyCoordinator` must react to VPN state changes to keep the subprocess routing correctly.

### TelegramProxyCoordinator

```kotlin
combine(
    tunnelController.state,
    configStore.config()
) { tunnelState, config ->
    // select upstream based on active engine's socksPort
}.collect { decision ->
    when (decision) {
        is Start -> mtgWrapper.start(args = buildArgs(decision))
        is Stop  -> mtgWrapper.stop()
    }
}
```

The coordinator is initialized in `OzeroApp.onCreate` via Hilt inject + `runCatching`. It must survive the app lifecycle, not be scoped to any single screen.

### Routing Logic

| Active Engine | `socksPort` | mtg upstream |
|---------------|-------------|-------------|
| WARP (AWG TUN) | `0` | None — inherits UID, traffic via TUN |
| ByeDPI / Hy2 / Xray | `>0` | `--socks5-proxy-url socks5://127.0.0.1:<port>` |

The WARP case works because `excludeSelf=false` in the VPN config — mtg's UID is included in the TUN, so outbound traffic is captured automatically.

### ProcessBuilder Launch

```kotlin
val process = ProcessBuilder(
    listOf(binaryPath, "run", "--secret", config.secret, "--bind", "0.0.0.0:${config.port}")
        + upstreamArgs
)
.directory(workDir)
.redirectErrorStream(true)
.start()
```

`binaryPath` is derived from `context.applicationInfo.nativeLibraryDir + "/libmtg.so"`. This field is accessed via `by lazy {}` in `MtgWrapper` to prevent initialization during Robolectric test setup (where `nativeLibraryDir` is null).

### Binary Versioning: binaries.lock.yaml

```yaml
mtg:
  version: "2.1.7"
  sha256:
    arm64-v8a: "abc123..."
    armeabi-v7a: "def456..."
    x86_64: "789ghi..."
```

`regen_lock.py` requires:
1. Downloaded `.so` files per ABI
2. `manifest.yaml` from the upstream release

Run after bumping the binary version. CI uses the lock to verify artifact integrity before committing.

### Test Isolation Traps

**Trap 1: UncompletedCoroutinesError**
`TelegramProxyCoordinator` launches a long-running `collect` loop. Running it in `testScope` leaves an uncompleted child coroutine:
```
// WRONG
coordinatorScope = testScope  // leaves uncompleted child

// RIGHT
coordinatorScope = CoroutineScope(testDispatcher + SupervisorJob())
// then in @AfterEach: coordinatorScope.cancel()
```

**Trap 2: Robolectric NPE on nativeLibraryDir**
`TelegramProxyService` must not access `context.applicationInfo.nativeLibraryDir` at field-init time. Robolectric returns null:
```kotlin
// WRONG
val wrapper = MtgWrapper(context.applicationInfo.nativeLibraryDir)

// RIGHT
val wrapper by lazy { MtgWrapper(context.applicationInfo.nativeLibraryDir) }
```
Also requires `@Config(application = Application::class)` on the test class to avoid Hilt's eager `@HiltAndroidApp` initialization.

**Trap 3: DataStore scope in tests**
`DataStoreTelegramConfigStore` uses a coroutine scope. Sharing `testScope` across tests causes `UncompletedCoroutinesError` on the second test. Fix: create per-test `datastoreScope` in `@BeforeEach`, cancel in `@AfterEach`.

## Related Concepts

- [[concepts/shell-mock-positional-arg-trap]] - Shell script fake binary traps encountered when writing MtgWrapper arg tests
- [[concepts/runtest-uncompleted-coroutines-trap]] - General pattern for long-running coroutine loops in runTest
- [[concepts/robolectric-hilt-eager-init-trap]] - by lazy + @Config fix for Robolectric + Hilt NPE
- [[concepts/native-binary-auto-update-pipeline]] - CI pipeline for prebuilt native binaries; binaries.lock.yaml fits same pattern
- [[concepts/go-runtime-process-isolation]] - Two Go runtimes cannot coexist in one process; mtg runs as separate process for this reason

## Sources

- [[daily/2026-05-14.md]] - engine-telegram implementation: TelegramProxyCoordinator, MtgWrapper, routing logic, binaries.lock.yaml, 3 test isolation traps fixed
