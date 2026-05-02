---
title: "Robolectric Room Migration Runtime Testing"
aliases: [migration-runtime-test, room-migration-testing, robolectric-sqlite]
tags: [android, testing, room, robolectric, database]
sources:
  - "daily/2026-05-01.md"
created: 2026-05-01
updated: 2026-05-01
---

# Robolectric Room Migration Runtime Testing

Room database migration correctness cannot be verified by source-pattern sentinels alone. A test that checks SQL strings at the source level does not prove the migration executes without error on a real SQLite engine. Runtime migration tests using Robolectric with `FrameworkSQLiteOpenHelperFactory` execute actual SQL against an in-process SQLite database, catching syntax errors, missing columns, and constraint violations that string-based tests miss.

## Key Points

- Source-pattern sentinels (asserting SQL strings exist in source) give false confidence — they verify text, not execution
- `FrameworkSQLiteOpenHelperFactory` provides a real SQLite engine inside Robolectric without an Android device or emulator
- Room's `MigrationTestHelper` is the standard tool, but manual `SupportSQLiteOpenHelper` with `FrameworkSQLiteOpenHelperFactory` gives finer control for schema inspection
- Six test cases cover the critical surface: schema creation, default values, data preservation across migration, idempotency, and enum-like column values
- Dependencies required: `androidx.room:room-testing`, `org.robolectric:robolectric` 4.13, `junit-vintage-engine` (JUnit 4 @Rule interop with JUnit 5 platform)
- `testOptions.unitTests.isIncludeAndroidResources = true` is mandatory in `build.gradle.kts` for Robolectric to find Android resources

## Details

### The Problem with Source-Pattern Tests

Ozero's initial `MigrationFourToFiveTest` was a source-pattern sentinel — it read the migration source code and asserted that the expected SQL strings were present. This caught regressions where the migration was accidentally deleted or modified, but could not detect:

- SQL syntax errors that SQLite rejects at runtime
- Column type mismatches between the migration ALTER TABLE and Room's expected schema
- Missing DEFAULT clauses that Room requires for new non-null columns
- Constraint violations when existing data conflicts with new schema requirements

The code review for v0.0.2-1 (concern C3) flagged this gap: if the CREATE TABLE syntax in `MIGRATION_4_5` contained a typo, the source-pattern test would still pass, but every user upgrading from v0.0.1 would crash on first launch.

### Runtime Test Architecture

The runtime test creates a real SQLite database at schema version 4, populates it with representative data, then applies `MIGRATION_4_5` and verifies the result. The test uses `FrameworkSQLiteOpenHelperFactory` (from `androidx.sqlite:sqlite-framework`) which provides Android's SQLite implementation through Robolectric's shadow layer.

Six test cases cover the migration surface:

1. **Schema creation** — migration runs without SQL error
2. **Default values** — new columns have correct defaults per Room `@ColumnInfo(defaultValue = ...)`
3. **Data preservation** — rows inserted at v4 survive migration to v5 with correct values
4. **Idempotency** — running migration twice does not throw (guards against `ALTER TABLE ADD COLUMN` on existing column)
5. **finalStatus values** — enum-like string column accepts all expected values (DISCONNECTED, FAILED, etc.)
6. **New table creation** — tables added in v5 exist with correct schema

### Visibility Change

`OzeroDatabase.MIGRATION_4_5` was changed from `private` to `internal` to allow the test (in the same module) to reference the migration object directly. This is a minimal visibility increase — `internal` keeps the migration hidden from other Gradle modules while exposing it within `:core-storage` for testing.

### Dependency Chain

The JUnit 4 ↔ JUnit 5 interop deserves note. Room's `MigrationTestHelper` uses JUnit 4 `@Rule` annotation. Ozero's test infrastructure uses JUnit 5 (Jupiter). `junit-vintage-engine` bridges the gap, allowing JUnit 4 rules to execute within a JUnit 5 test runner. Without this bridge, `@Rule` annotations are silently ignored and the helper is never initialized.

## Related Concepts

- [[concepts/feature-branch-code-review-2026-05-01]] - C3 concern that triggered this runtime test requirement
- [[concepts/vpnservice-builder-traps]] - Similar pattern of "looks correct but fails at runtime" requiring empirical validation

## Sources

- [[daily/2026-05-01.md]] - C3 fix: MigrationFourToFiveRuntimeTest with 6 test cases using FrameworkSQLiteOpenHelperFactory; source-pattern sentinel identified as insufficient
