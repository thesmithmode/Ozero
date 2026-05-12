---
title: "Mock SOCKS5 Server CI Fragility: repeat=N Exhaustion"
aliases: [mock-server-fragility, socks5-mock-repeat, ci-flaky-mock]
tags: [testing, ci, byedpi, gotcha, flaky-tests]
sources:
  - "daily/2026-05-09.md"
  - "daily/2026-05-10.md"
created: 2026-05-09
updated: 2026-05-10
---

# Mock SOCKS5 Server CI Fragility: repeat=N Exhaustion + Real-Time Clock Mismatch

`ByeDpiEngineTest.startSuccessWhenSocksPortReady` used a mock SOCKS5 server with `repeat=N` (fixed iteration count) in `acceptSocks5InBackground`. Under CI load, the first probe connection (500ms timeout) could fail, consuming one of the N iterations. Subsequent probes from the engine's readiness checker connected to the OS TCP backlog (accepted by kernel but no server-side handler), received no SOCKS5 handshake response, and timed out after 5 seconds — producing `StartResult.Failure` despite the engine being correctly configured.

## Key Points

- Mock server with `repeat=N` exits accept loop after N connections — any extra connection attempt gets TCP-accepted by OS backlog but no response
- CI load causes timing variations: first probe may connect and disconnect before mock processes it, consuming one iteration
- Symptom: test green in isolation, flaky under CI load (passed in runs 25596700209, 25596517936; failed in subsequent run)
- Fix: mock server should accept unlimited connections until explicitly closed — `while (!closed) { accept() }` pattern
- Rule: mock servers in tests must handle unbounded connection count; `repeat=N` is inherently fragile under concurrent load

## Details

### The Exhaustion Mechanism

The `acceptSocks5InBackground` test helper launched a server socket that accepted exactly N incoming TCP connections, performed SOCKS5 handshake on each, then exited the loop. The engine's `Socks5HandshakeProbe` sends probe connections to verify the SOCKS5 proxy is ready. Under normal conditions (local development, low system load), the probe connects once, handshake succeeds, and the engine transitions to `Running`.

Under CI load (multiple Gradle test workers, OS scheduling pressure), timing changes:

1. Probe connects at t=0 with 500ms timeout
2. Mock server hasn't called `accept()` yet (thread scheduling delay)
3. OS TCP stack accepts the connection into backlog (SYN-ACK sent)
4. Probe receives SYN-ACK, connection established at TCP level
5. Probe waits for SOCKS5 handshake data — mock still hasn't processed the accept
6. Probe times out at t=500ms, closes connection
7. Mock finally calls `accept()`, gets the closed connection, counts it as iteration 1
8. Engine sends real probe, mock processes it correctly — but if N=1, mock has already exhausted its count

The result: the engine's second probe connects to the OS backlog (no server thread accepting), waits 5 seconds for SOCKS5 data that never comes, and reports `StartResult.Failure`.

### The Fix: Unbounded Accept

```kotlin
fun acceptSocks5InBackground(serverSocket: ServerSocket) {
    thread {
        while (!serverSocket.isClosed) {
            runCatching {
                val client = serverSocket.accept()
                handleSocks5Handshake(client)
            }
        }
    }
}
```

The server accepts connections until `serverSocket.close()` is called in `@AfterEach`. No iteration count, no exhaustion possible. Additional probes from timing variations are handled gracefully.

### CI Masking

This flaky test was hidden by CI job dependency masking ([[concepts/ci-job-dependency-masking]]): a ktlint failure in the `kotlin-style` job prevented test jobs from starting. The developer fixed ktlint first, then discovered the flaky test on the next run. Two CI runs were needed to see both problems — a structural limitation of the `needs:` chain.

## Related Concepts

- [[concepts/test-io-thread-zombie-trap]] - Related test infrastructure issue: `Thread.sleep` in mocks causes zombie threads; both are about mock lifecycle management in tests
- [[concepts/ci-job-dependency-masking]] - ktlint failure masked this flaky test; same `needs:` pattern as detekt masking compile errors
- [[concepts/junit-platform-silent-skip]] - Another test infrastructure trap producing misleading CI results

## Root Cause 2: System.currentTimeMillis() Outer Loop vs Virtual Clock in runTest

After the unbounded accept fix, `startSuccessWhenSocksPortReady` continued to fail on CI. Second root cause: `waitSocksReady` used `System.currentTimeMillis()` for the outer retry loop while running inside `runTest`.

### The Mechanism

```kotlin
// BROKEN: outer loop uses real wall-clock time
private suspend fun waitSocksReady(port: Int): Long {
    val started = System.currentTimeMillis()
    while (System.currentTimeMillis() - started < READY_TIMEOUT_MS) {  // 5_000 ms real
        val ok = runCatching {
            Socks5HandshakeProbe.probe("127.0.0.1", port, readyProbeTimeoutMs)  // 500 ms per-probe real
        }.isSuccess
        if (ok) return System.currentTimeMillis() - started
        delay(READY_RETRY_MS)
    }
    return -1
}
```

`Socks5HandshakeProbe.probe` is a `suspend fun` using `withContext(Dispatchers.IO)` → it runs on a real IO thread and consumes **real wall-clock time** regardless of `runTest`'s virtual scheduler. With `readyProbeTimeoutMs = 5_000` and `READY_TIMEOUT_MS = 5_000`: one failed probe exhausts the entire outer loop budget → 0 retries → `StartResult.Failure` on a loaded CI runner where the daemon thread hasn't had CPU time yet.

### The Fix: withTimeoutOrNull uses virtual clock in tests

```kotlin
private suspend fun waitSocksReady(port: Int): Long {
    val started = System.currentTimeMillis()
    withTimeoutOrNull(readyTotalTimeoutMs) {   // uses TestCoroutineScheduler virtual time in runTest
        while (true) {
            val ok = try {
                Socks5HandshakeProbe.probe("127.0.0.1", port, readyProbeTimeoutMs)
                true
            } catch (e: CancellationException) {
                throw e  // never swallow CancellationException
            } catch (_: Throwable) {
                false
            }
            if (ok) return@withTimeoutOrNull
            delay(READY_RETRY_MS)  // 100ms virtual per retry
        }
    } ?: return -1
    return System.currentTimeMillis() - started
}
```

Test setup: `readyProbeTimeoutMs = 200, readyTotalTimeoutMs = 30_000`. In `runTest`:
- Connection-refused probe: ~1ms real, 0ms virtual
- `delay(100)`: 0ms real, 100ms virtual
- 300 retries = 30 000ms virtual = ~300ms real
- Failure tests remain fast; success tests break early on first successful probe.

### Rules

- `System.currentTimeMillis()` in timeout loops inside `suspend fun` tested with `runTest` → **BROKEN**: reads real time, `delay()` advances virtual time; loop appears to exhaust instantly under load
- `withTimeoutOrNull(n)` inside `runTest` → uses TestCoroutineScheduler virtual time → safe
- `withContext(Dispatchers.IO)` in `suspend fun` under `runTest` → still real IO thread, real clock for blocking ops
- `runCatching` catching `CancellationException` is an anti-pattern; always re-throw explicitly

## Root Cause 3: Static `Socks5HandshakeProbe` — не injectable → тесты зависят от реального IO

После fix #2 (`withTimeoutOrNull`) тест `probeSuccessWhenSocketListens` стал флакать. Причина: `Socks5HandshakeProbe` — статический object, не инжектируемый. Тесты использовали реальный `ServerSocket(0)` + daemon thread для имитации SOCKS5 сервера. Под нагрузкой CI (2-core runner) timing гарантии нарушаются: probe может получить connection refused или половинчатый handshake.

### Структурный Fix: Injectable probe lambda

```kotlin
class ByeDpiEngine(
    private val proxy: ByeDpiProxyContract = ByeDpiProxy(),
    private val socksProbe: suspend (String, Int, Int) -> Long = Socks5HandshakeProbe::probe,
    ...
) : EnginePlugin
```

Все вызовы `Socks5HandshakeProbe.probe(...)` заменены на `socksProbe(...)`. Тесты инжектируют детерминированную лямбду:

```kotlin
// setUp (happy path):
engine = ByeDpiEngine(proxy, socksProbe = { _, _, _ -> 1L })

// failure tests:
val failEngine = ByeDpiEngine(proxy, socksProbe = { _, _, _ -> throw IOException("refused") }, readyTotalTimeoutMs = 500)

// probe fails after success start:
var callCount = 0
val localEngine = ByeDpiEngine(proxy, socksProbe = { _, _, _ ->
    if (++callCount == 1) 1L else throw IOException("refused")
})
```

ServerSocket + daemon thread полностью удалены из тестов. Тесты не зависят от real IO, порты не выделяются, timing races невозможны. `proxyRunning: CountDownLatch` сохранён только там где нужно блокировать `proxyJob` (тест `probeSuccessWhenSocketListens` — чтобы `activeSocksPort` не обнулился до вызова `probe()`).

### Также исправлено: `catch (e: Exception)` в `probe()` глотал `CancellationException`

```kotlin
// BROKEN
} catch (e: Exception) { ProbeResult.Failure(...) }

// FIXED
} catch (e: CancellationException) { throw e }
} catch (_: Throwable) { ProbeResult.Failure("connection refused") }
```

### Правило

Тесты suspend функций, использующих `withContext(Dispatchers.IO)` → инжектировать зависимость. Статические объекты с реальным IO — неизбежный источник flakiness под CI нагрузкой.

## Sources

- [[daily/2026-05-09.md]] - Session 16:19: `ByeDpiEngineTest.startSuccessWhenSocksPortReady` flaky on CI; root cause = `acceptSocks5InBackground` with `repeat` limit exhausted under load; fix = unlimited accept until socket close; ktlint masking delayed discovery
- [[daily/2026-05-09.md]] - Session 12:27: first observation of flaky test — green in runs 25596700209/25596517936, failed in next run
- [[daily/2026-05-10.md]] - Session: second root cause after unbounded accept fix — `System.currentTimeMillis()` outer loop vs virtual clock; fix = `withTimeoutOrNull(readyTotalTimeoutMs)` + injectable params
