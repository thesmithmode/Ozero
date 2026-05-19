---
title: "FileChannel.lock() POSIX Per-Process Limitation"
aliases: [filechannel-lock-posix, file-lock-per-pid, jvm-file-lock-threads]
tags: [java, concurrency, gotcha, testing, posix]
sources:
  - "daily/2026-05-16.md"
created: 2026-05-16
updated: 2026-05-16
---

# FileChannel.lock() POSIX Per-Process Limitation

`FileChannel.lock()` on Linux/POSIX acquires a lock at the OS process (PID) level, not at the thread level. Multiple threads within the same JVM process can all acquire "exclusive" locks on the same file simultaneously — the OS sees them as the same process and grants the lock to each. This makes `FileChannel.lock()` useless for serializing concurrent access within a single JVM, including parallel Gradle test workers running in the same daemon process.

## Key Points

- POSIX `fcntl(F_SETLK)` (underlying `FileChannel.lock()`) is per-PID — all threads in one process share the same lock identity
- Two threads calling `FileChannel.lock()` on the same file both succeed — no serialization within the JVM
- Parallel Gradle test workers run in the same JVM daemon → file locks don't isolate test cases
- Fix: use `java.util.concurrent.locks.ReentrantLock` (or `ConcurrentHashMap<Path, ReentrantLock>`) for in-process serialization alongside `FileChannel.lock()` for cross-process serialization
- On Windows, `FileChannel.lock()` IS per-handle and provides thread-level exclusion — platform-dependent behavior

## Details

### The POSIX Lock Model

POSIX file locking via `fcntl(F_SETLK/F_SETLKW)` — which `java.nio.channels.FileChannel.lock()` delegates to on Linux and macOS — uses the process ID as the lock owner. The kernel maintains a table mapping (file, byte-range) → PID. When two threads in PID 12345 both request an exclusive lock on the same file region, the kernel sees the same PID for both requests and grants both — the process already "owns" the lock.

This is fundamentally different from Windows, where file locks are per-handle. Two `FileChannel` instances in the same Windows process opening the same file get independent handles, and the second lock attempt blocks or fails.

### The Ozero Discovery

`Sha256Verifier.withFileLock()` in `buildSrc` used `FileChannel.lock()` to serialize access to downloaded binary artifacts during SHA verification. When multiple Gradle test tasks ran in parallel (same daemon JVM), each test's `withFileLock` call succeeded immediately — no serialization occurred. Two tests verifying the same artifact file could read partially-written data.

The fix added a `ConcurrentHashMap<Path, ReentrantLock>` as an in-process synchronization layer:

```kotlin
private val inProcessLocks = ConcurrentHashMap<Path, ReentrantLock>()

fun withFileLock(path: Path, block: () -> T): T {
    val processLock = inProcessLocks.computeIfAbsent(path) { ReentrantLock() }
    processLock.lock()
    try {
        FileChannel.open(path, WRITE).use { channel ->
            channel.lock().use {
                return block()
            }
        }
    } finally {
        processLock.unlock()
    }
}
```

The `ReentrantLock` serializes threads within the JVM; `FileChannel.lock()` serializes across JVM processes (e.g., multiple Gradle daemons or CI workers).

### When FileChannel.lock() Is Sufficient

`FileChannel.lock()` alone works correctly when:
- Lock contention is only between separate OS processes (different PIDs)
- The JVM has only one thread accessing the locked file
- The platform is Windows (per-handle locking)

It is insufficient when:
- Multiple threads in the same JVM access the same file (parallel test workers, coroutine dispatchers)
- The code runs on Linux/macOS in a multi-threaded context

### Testing Implications

Gradle test tasks running with `maxParallelForks > 1` in the same daemon share PID. Any test that relies on file locking for isolation must use in-process locks as well. This is particularly relevant for `buildSrc` code that manages shared build artifacts — the build system itself runs multi-threaded even when individual test classes are sequential.

## Related Concepts

- [[concepts/test-io-thread-zombie-trap]] - Another concurrency trap in test infrastructure where JVM-level resource management differs from expectations
- [[concepts/gene-memory-concurrency-traps]] - HashMap race in shared state; same class of problem — JVM concurrency primitives insufficient without explicit synchronization

## Sources

- [[daily/2026-05-16.md]] - Session 12:09: `Sha256Verifier.withFileLock` using `FileChannel.lock()` failed to serialize parallel Gradle test workers (same PID); fix = `ConcurrentHashMap<Path, ReentrantLock>` for in-process locking
