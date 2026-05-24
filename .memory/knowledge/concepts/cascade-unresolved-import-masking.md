---
title: "Cascade Unresolved Import Masking in Kotlin"
aliases: [import-cascade-error, unresolved-import-mask, kotlin-import-cascade]
tags: [kotlin, android, debugging, compilation]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Cascade Unresolved Import Masking in Kotlin

When a Kotlin file imports a non-existent class, the Kotlin compiler produces `Unresolved reference: ClassName` for the import itself. This cascades into spurious "Unresolved reference" errors for every use of that class in the file — and for any symbol that happens to appear on the same line. The apparent errors at usage sites are phantom; fixing the root import eliminates them all.

## Key Points

- One bad import (`import com.example.pkg.NonExistentClass`) generates cascade "Unresolved reference" errors for all usage sites of that class
- The cascade can make the error list appear 5-10x longer than the actual problem count
- Symbols on the same line as a phantom class usage also appear as unresolved (e.g., `uuid` in `uuid = nonExistentMethod()`)
- Fix strategy: grep for ALL unresolved imports first, fix them, then reassess remaining errors
- In Kotlin/Gradle, the first error in a file often causes all subsequent errors in that file to be spurious cascade
- Importing non-existent model classes (e.g., `WireGuardServer`, `SingboxServerJson` that were never created) causes `"Unresolved reference: URI"` on unrelated code — the real error (missing import) masks completely different errors (correct `java.net.URI` usage); always verify imported classes exist before debugging usage-site errors

## Details

### Incident Pattern

During `engine-singbox` development (2026-05-24), `SingboxSubscriptionParser.kt` imported:

```kotlin
import com.example.ozero.singbox.data.model.WireGuardServer   // didn't exist
import com.example.ozero.singbox.data.model.SingboxServerJson  // didn't exist
import com.example.ozero.singbox.data.model.ShareLink          // didn't exist
```

The CI log reported `Unresolved reference: URI` (the actual `java.net.URI` import), `Unresolved reference: uuid`, and `Unresolved reference: getUserInfo`. These all appeared to be legitimate logic errors but were actually cascade failures from the three phantom imports above.

After removing the non-existent imports and creating the missing classes, the `URI`, `uuid`, and `getUserInfo` errors disappeared entirely.

### Diagnostic Approach

When facing a large list of "Unresolved reference" errors in a file:

1. **Triage imports first**: check every import line against existing file paths
2. **Create missing classes** or remove non-existent imports before interpreting usage-site errors
3. **Recompile** — the post-fix error count may drop dramatically

This is different from the standard approach of reading errors top-to-bottom and fixing each one. For cascade scenarios, bottom-up or import-first is more efficient.

### Why This Matters in Ozero

The `engine-singbox` module was built incrementally with many new classes. Parser methods referenced server type classes (`VlessServer`, `WireGuardServer`) by names that didn't match the sealed class hierarchy (`SingboxServer.Vless`, `SingboxServer.WireGuard`). Each mismatch introduced phantom imports, generating 5-15 cascade errors per bad import.

## Related Concepts

- [[concepts/ci-gradle-log-reading]] - Method to read CI errors; important context for diagnosing cascade
- [[concepts/kotlin-expression-body-return-trap]] - Another class of Kotlin compile errors found in same session
- [[concepts/sentinel-fqn-desync]] - Related: FQN mismatches after class rename also cause cascade
- [[concepts/extension-function-import-migration-trap]] - Import-related compile failures

## Sources

- [[daily/2026-05-24.md]] — Session 19:13: engine-singbox parser had imports of `WireGuardServer`, `SingboxServerJson`, `ShareLink` that didn't exist; caused cascade "Unresolved reference: URI", "uuid", "getUserInfo" errors that appeared to be logic bugs; removing phantom imports cleared the cascade; `WireGuardServer` and `SingboxServerJson` were never created as standalone classes — correct references are `SingboxServer.WireGuard` and the sealed class hierarchy; java.net.URI usage was correct but masked by the phantom import failure
