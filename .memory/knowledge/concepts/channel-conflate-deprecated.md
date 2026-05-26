---
title: "Channel.conflate() Extension Deprecated"
aliases: [channel-conflated, conflate-channel]
tags: [kotlin, coroutines, channel]
sources:
  - "daily/2026-05-25.md"
created: 2026-05-25
updated: 2026-05-25
---

# Channel.conflate() Extension Deprecated

The `Channel.conflate()` extension function on `ReceiveChannel` was deprecated and removed from `kotlinx.coroutines`. Calling `.conflate()` on an existing `Channel` object compiles in old versions but fails in current ones. The correct pattern is to pass `Channel.CONFLATED` at construction time.

## Key Points

- `channel.conflate()` — deprecated extension, may fail to compile in current kotlinx.coroutines
- Correct pattern: `Channel<T>(Channel.CONFLATED)` at construction
- The error surfaces as an `Unresolved reference: conflate` compile error in CI
- Pattern affected: `SingboxViewModel` had `refreshChannel.conflate()` causing CI failure
- `Channel(Channel.UNLIMITED)` and other capacity constants remain unchanged

## Details

In `SingboxViewModel`, a `Channel<RefreshAction>` was used to serialize refresh requests. The channel had `.conflate()` appended as a post-creation call to drop intermediate emissions. This pattern worked in older coroutines versions where `conflate()` was an extension on `ReceiveChannel`, but the extension was removed.

The fix replaces the two-step pattern:
```kotlin
// Before (broken)
val channel = Channel<RefreshAction>(Channel.UNLIMITED)
val conflated = channel.conflate()

// After (correct)
val channel = Channel<RefreshAction>(Channel.CONFLATED)
```

The fix was straightforward but required CI to catch — the error is a compile failure (`Unresolved reference`) that only appears when building with current coroutines versions.

## Related Concepts

- [[concepts/collect-vs-collectlatest-restart-semantics]] - Related Flow conflation behavior
- [[concepts/runtest-uncompleted-coroutines-trap]] - Other coroutines-version-sensitive patterns
- [[concepts/ci-gradle-log-reading]] - Required to find the actual compile error in CI

## Sources

- [[daily/2026-05-25.md]] — `SingboxViewModel` CI failure: `Unresolved reference: conflate` on `Channel`; fix: replace `.conflate()` call with `Channel(Channel.CONFLATED)` constructor argument
