---
title: "Kotlin Imports Must Be at File Level"
aliases: [import-inside-function, kotlin-import-scope, local-import-kotlin]
tags: [kotlin, syntax, android, compilation]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Kotlin Imports Must Be at File Level

Kotlin does not support import declarations inside function bodies, class bodies (other than at file level), or any nested scope. An `import` statement inside a function body causes a compile error. This differs from some other languages (e.g., Python) where imports inside functions are valid. Additionally, `kotlin.replaceAll` (a JVM extension) is not available as a direct method on `MutableList` — it requires a Java `UnaryOperator` cast or an index-based loop instead.

## Key Points

- `import com.example.Foo` inside a function body → compile error: `Expecting member declaration`
- All imports must appear at the top of the file, before class/function declarations
- `MutableList.replaceAll(UnaryOperator)` requires unsafe Java cast in Kotlin; use `forEachIndexed { i, v -> list[i] = transform(v) }` or `indexOfFirst` + manual assignment instead
- `List.replaceAll` from `java.util.List` is accessible in Kotlin but the lambda type mismatch causes cast issues
- PowerShell: `cat <<'EOF'` is interpreted as the `<` operator, not a heredoc — use Bash for multi-line strings in git commits

## Details

### Import Scope Error

During `engine-singbox` P4 implementation, `FakeSubscriptionGroupDao` accidentally contained an `import` statement inside a function body:

```kotlin
fun update(group: SubscriptionGroup) {
    import com.example.ozero.singbox.data.db.SubscriptionGroupEntity  // COMPILE ERROR
    // ...
}
```

The error message `Expecting member declaration` at the import line is not immediately obvious — it looks like a syntax error unrelated to imports. The fix is always to move the import to the file top.

### Kotlin MutableList replaceAll Trap

`kotlin.replaceAll` appears in autocomplete but requires a Java `UnaryOperator<T>`, which needs an unsafe cast in Kotlin:

```kotlin
// WRONG — requires cast, fragile
list.replaceAll { transform(it) as @Suppress("UNCHECKED_CAST") T }

// CORRECT — explicit index loop
list.forEachIndexed { i, item -> list[i] = transform(item) }

// CORRECT — indexOfFirst + set
val idx = list.indexOfFirst { it.id == target.id }
if (idx >= 0) list[idx] = updated
```

The `indexOfFirst` pattern was used in `FakeSubscriptionGroupDao` to replace `list.replaceAll(UnaryOperator)`.

### PowerShell Heredoc for Git Commits

PowerShell 5.1 treats `cat <<'EOF'` as the `<` input redirection operator, not a heredoc. Git commit messages with multi-line strings must use PowerShell here-strings (`@'...'@`) or a Bash shell:

```powershell
# CORRECT PowerShell
git commit -m @'
FEAT: добавить модуль singbox-room
'@
```

Or switch to Bash:
```bash
git commit -m "$(cat <<'EOF'
FEAT: добавить модуль singbox-room
EOF
)"
```

## Related Concepts

- [[concepts/kotlin-expression-body-return-trap]] - Another Kotlin syntax trap from the same session
- [[concepts/cascade-unresolved-import-masking]] - Phantom imports cause cascade errors
- [[concepts/ci-gradle-log-reading]] - How to diagnose compile errors from CI

## Sources

- [[daily/2026-05-24.md]] — Session 19:34: `import` inside function body in FakeSubscriptionGroupDao = compile error; `kotlin.replaceAll` requires Java UnaryOperator cast; PowerShell `cat <<'EOF'` not valid heredoc syntax
