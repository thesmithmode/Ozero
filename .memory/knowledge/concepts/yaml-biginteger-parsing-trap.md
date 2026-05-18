---
title: "YAML BigInteger Parsing Trap for All-Digit Strings"
aliases: [yaml-biginteger, yaml-numeric-string, snakeyaml-sha-parsing]
tags: [yaml, parsing, gotcha, build, kotlin]
sources:
  - "daily/2026-05-16.md"
created: 2026-05-16
updated: 2026-05-16
---

# YAML BigInteger Parsing Trap for All-Digit Strings

YAML parsers (including SnakeYAML used by Gradle/Kotlin tooling) interpret all-digit strings without quotes as numeric types. A SHA-256 hash like `39af2ae9` (if it happens to be all hex digits parseable as a number) or a commit hash like `83a10ccf` is parsed as `BigInteger`, not `String`. Using `as? String` on the parsed value returns `null` silently. The fix is always `.toString()` when reading string-semantic fields from YAML.

## Key Points

- YAML spec: unquoted all-digit values are parsed as integers; long values become `BigInteger` in JVM parsers
- `as? String` on a `BigInteger` value returns `null` — no exception, no warning, just silent data loss
- Affects: SHA hashes, commit SHAs, numeric build IDs, version strings like `20260516` — any field that looks numeric but is semantically a string
- Fix: always use `.toString()` instead of `as? String` when reading hash/SHA/commit fields from YAML
- In Ozero: `LockFileParser.req()` used `as? String` for `source_commit` field in `binaries.lock.yaml` — all-digit commit hashes silently dropped

## Details

### The Parsing Mechanism

The YAML 1.1 specification (used by most JVM YAML parsers including SnakeYAML) defines implicit typing rules. An unquoted value that matches the pattern for an integer is parsed as a numeric type. For values that exceed `Long.MAX_VALUE`, SnakeYAML promotes to `java.math.BigInteger`. This applies to:

- Git commit SHAs (40 hex chars, but some commits are all-decimal like `1234567890`)
- SHA-256 hashes (64 hex chars)
- Truncated hashes (8+ chars) used as tags or identifiers

In `binaries.lock.yaml`, the `source_commit` field stores an 8-character truncated commit SHA. When the commit hash happens to be all decimal digits (e.g., `83a10ccf` is hex but `12345678` would be numeric), SnakeYAML parses it as `BigInteger`. The Kotlin code `yamlMap["source_commit"] as? String` performs a safe cast that returns `null` because `BigInteger` is not `String`.

### The Silent Failure

The `as?` operator in Kotlin is designed for safe type narrowing — it returns `null` instead of throwing `ClassCastException`. This is normally desirable, but when the developer expects a `String` and the YAML parser returns `BigInteger`, the null propagates silently through the code. In `LockFileParser`, this caused the `source_commit` field to be `null` in the parsed `LockEntry`, which downstream code treated as "no commit specified" rather than a parse error.

### Prevention

For any YAML field that is semantically a string but may contain all-digit values:

```kotlin
// BROKEN: returns null for BigInteger values
val commit = yamlMap["source_commit"] as? String

// CORRECT: converts any type to its string representation
val commit = yamlMap["source_commit"]?.toString()
```

Alternatively, quote the value in YAML source: `source_commit: "83a10ccf"` — but this requires control over the YAML generation side, which may not always be possible.

### Broader Applicability

This trap affects any JVM project that parses YAML with hash/SHA/ID fields:
- CI/CD pipeline configurations
- Lock files (npm/yarn/gradle lock analogs)
- Docker Compose files with numeric service names
- Ansible inventory with numeric host identifiers

## Related Concepts

- [[concepts/byedpi-args-parsing]] - Another parsing trap where implicit type conversion causes silent data loss
- [[concepts/native-binary-auto-update-pipeline]] - binaries.lock.yaml is the file where this YAML trap was discovered

## Sources

- [[daily/2026-05-16.md]] - Session 12:09: `LockFileParser.req()` used `as? String` for YAML `source_commit` — BigInteger returned null silently; fix = `.toString()`
