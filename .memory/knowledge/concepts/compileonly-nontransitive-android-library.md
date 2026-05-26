---
title: "compileOnly Non-Transitive in Android Library Modules"
aliases: [compileonly-transitive, gradle-compileonly-android]
tags: [android, gradle, compileonly, transitive]
sources:
  - "daily/2026-05-25.md"
created: 2026-05-25
updated: 2026-05-25
---

# compileOnly Non-Transitive in Android Library Modules

In Android Gradle library (AAR) modules, `compileOnly` dependencies are **not transitive**. A consumer of the library module does not see `compileOnly` declarations from that library; each consuming module must add its own `compileOnly` declaration if it needs to compile against those classes.

## Key Points

- `compileOnly` in Android Gradle library = visible only to that module's compilation, NOT to consumers
- Transitive behavior applies only to `api` and `implementation` configurations
- Symptom: `Cannot access 'X' which is a supertype of 'Y'` compile error in a consumer module
- Fix: add `compileOnly` to each module that directly extends/implements classes from the stub
- Affected case: `go-stubs.jar` (gomobile `go.*` classes) in `singbox-core` → invisible to `singbox-process`

## Details

### The Singbox go-stubs Pattern

`libbox.so` (gomobile-generated) requires `go.*` Java classes (`go.Seq`, `go.Seq.Proxy`, etc.) to be present in the classpath at compile time. These classes are stripped from the main DEX to avoid conflicts with URnetwork's `go.*` classes (see [[concepts/gomobile-go-seq-multi-sdk-conflict]]).

The workaround creates a `go-stubs.jar` containing only the `go.*` classes from the libbox build. Adding `compileOnly(rootProject.file("singbox-core/libs-stubs"))` to `singbox-core/build.gradle.kts` only satisfies compilation of `singbox-core` itself.

`singbox-process` depends on `singbox-core` and has its own classes extending `go.Seq.Proxy` (via generated gomobile stubs). Because `compileOnly` is not transitive:

```
singbox-core: compileOnly(go-stubs) ← visible only here
singbox-process: implementation(singbox-core) ← go-stubs NOT visible
```

CI error: `Cannot access 'go.Seq.Proxy' which is a supertype of 'io.nekohasekai.libbox.StatusMessage'`

Fix: explicitly add the stubs to the consuming module:
```kotlin
// singbox-process/build.gradle.kts
compileOnly(rootProject.file("singbox-core/libs-stubs"))
```

### General Rule

Any class hierarchy that crosses module boundaries via `compileOnly` stubs requires each module in the chain to have its own `compileOnly` declaration. This is different from Java classpath inheritance where transitive compile-time dependencies were available.

## Related Concepts

- [[concepts/kapt-per-module-requirement]] - Same pattern: kapt annotations also not transitive
- [[concepts/gomobile-go-seq-multi-sdk-conflict]] - Why go-stubs pattern exists
- [[concepts/hilt-cross-process-injection]] - Another non-transitive Gradle dependency gotcha

## Sources

- [[daily/2026-05-25.md]] — `singbox-process:compileDebugKotlin` CI failure: `Cannot access 'go.Seq.Proxy'`; first fix (compileOnly in singbox-core only) did not help; second fix (compileOnly added directly to singbox-process) made CI green; lesson: compileOnly not transitive in Android library modules
