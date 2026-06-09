---
title: "Hilt @AssistedInject: Mixing Regular and Assisted Dependencies"
aliases: [assistedinject-regular-deps, hilt-assisted-inject-pattern, assisted-factory-deps]
tags: [android, hilt, dependency-injection, gotcha, engine]
sources:
  - "daily/2026-05-10.md"
created: 2026-05-10
updated: 2026-06-09
---

# Hilt @AssistedInject: Mixing Regular and Assisted Dependencies

When adding a new dependency to a class that uses `@AssistedInject`, the dependency goes as a regular constructor parameter (resolved by Hilt's DI graph) alongside the `@Assisted` parameters (provided at runtime via the `@AssistedFactory`). Confusing the two — marking a Hilt-resolvable dependency as `@Assisted` or omitting a binding for it — causes either a compile error (missing factory parameter) or a runtime `UninitializedPropertyAccessException`.

## Key Points

- `@AssistedInject` constructor accepts two kinds of params: regular (Hilt-resolved from DI graph) and `@Assisted` (caller-provided at creation time)
- Adding a new injectable dependency (e.g., `ByeDpiConnectionProbe`): add as regular param + ensure Hilt binding exists (`@Binds` or `@Provides` in a `@Module`)
- Adding as `@Assisted` by mistake: the `@AssistedFactory` interface must then include it as a factory method param — semantically wrong for a singleton dependency
- In Ozero: all engine plugins use `@AssistedInject` because they receive runtime config (`ByeDpiProxyContract`, `EngineConfig`) while also needing singleton services
- AI code agents frequently make errors with `@AssistedInject` — manual verification of DI wiring is required after subagent edits

## Details

### The Two Parameter Types

Hilt's `@AssistedInject` is designed for classes that need both Hilt-managed singletons and runtime-provided values. The constructor declares both:

```kotlin
class ByeDpiEngine @AssistedInject constructor(
    // Regular: resolved by Hilt from DI graph
    private val connectionProbe: ByeDpiConnectionProbe,
    // Assisted: provided by caller via factory
    @Assisted private val proxy: ByeDpiProxyContract,
) : EnginePlugin
```

The corresponding factory interface only declares `@Assisted` parameters:

```kotlin
@AssistedFactory
interface Factory {
    fun create(proxy: ByeDpiProxyContract): ByeDpiEngine
}
```

Hilt generates code that: (1) retrieves `ByeDpiConnectionProbe` from the DI graph at factory creation time, (2) accepts `proxy` as a runtime argument in `create()`, (3) passes both to the constructor.

### The Ozero Discovery

During the v0.0.9 ByeDpi refactoring, `Socks5HandshakeProbe` (a static object) was replaced with an injectable `ByeDpiConnectionProbe` interface. Four subagents were dispatched in parallel to implement the change. Subagent 4, responsible for wiring `ByeDpiEngine.kt`, added the probe as a field with a default value instead of a constructor parameter:

```kotlin
// WRONG: subagent approach — bypasses DI entirely
class ByeDpiEngine @AssistedInject constructor(
    @Assisted private val proxy: ByeDpiProxyContract,
) : EnginePlugin {
    private val socks5HandshakeProbe: ByeDpiConnectionProbe = Socks5HandshakeProbe()
}
```

This compiles but defeats the purpose of DI — tests cannot inject a mock probe. The correct approach required: (1) adding `connectionProbe: ByeDpiConnectionProbe` as a regular constructor parameter, (2) creating `ByeDpiModule` with `@Binds` to map `ByeDpiConnectionProbe` to `Socks5HandshakeProbe`, (3) manual correction of the subagent's output.

### When to Use @Assisted vs Regular

| Dependency characteristic | Parameter type | Example |
|--------------------------|---------------|---------|
| Singleton / scoped service | Regular (no annotation) | `ByeDpiConnectionProbe`, `SettingsRepository` |
| Per-instance runtime config | `@Assisted` | `ByeDpiProxyContract`, `EngineConfig` |
| Value that changes per creation call | `@Assisted` | port number, config string |
| Interface with Hilt binding | Regular | any `@Binds`/`@Provides` target |

Rule of thumb: if the value comes from user input or varies per engine start, it's `@Assisted`. If it's a service that lives in the DI graph, it's regular.

### Required Hilt Module

Every regular parameter in an `@AssistedInject` constructor must have a Hilt binding. For interface→implementation bindings:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class ByeDpiModule {
    @Binds
    abstract fun bindConnectionProbe(
        impl: Socks5HandshakeProbe
    ): ByeDpiConnectionProbe
}
```

Missing this module causes a Hilt compile error: "ByeDpiConnectionProbe cannot be provided without an @Provides-annotated method."

## Related Concepts

- [[concepts/byedpi-mock-server-ci-fragility]] - The refactoring that introduced injectable probe and exposed this @AssistedInject pattern
- [[concepts/byedpi-connection-probe-injection-contract]] - The extracted probe dependency that must remain a regular injected dependency, not an assisted runtime parameter
- [[concepts/hilt-di-native-library-failure]] - Earlier Hilt DI trap: native library load in provider breaks entire graph; same DI architecture

## Sources

- [[daily/2026-05-10.md]] - Session 14:48: subagent added probe as field instead of constructor param; manual correction to @AssistedInject constructor; ByeDpiModule created for binding
