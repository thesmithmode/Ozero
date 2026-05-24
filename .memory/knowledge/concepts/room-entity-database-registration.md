---
title: "Room Entity Registration and Database Version Bump"
aliases: [room-entity-registration, appDatabase-entity-add, room-migration-version]
tags: [room, android, database, migration]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Room Entity Registration and Database Version Bump

Adding a new Room entity to an existing `AppDatabase` requires three coordinated changes: (1) adding the entity class to the `@Database(entities = [...])` annotation, (2) incrementing `version`, and (3) providing a migration or enabling `fallbackToDestructiveMigration`. Skipping any of these causes a runtime crash — not a compile error — which CI may not catch if only unit tests run.

## Key Points

- New entity class must be listed in `@Database(entities = [Foo::class, NewEntity::class, ...])`
- `version` must be incremented by at least 1 from the current value
- Provide `addMigrations(MIGRATION_N_N1)` or `fallbackToDestructiveMigration()` for non-production schemas
- Compile succeeds without these changes; crash happens on first DB access at runtime
- Forgetting the entity list entry means the DAO for that entity will compile but fail at runtime: `Table not found`

## Details

### Three-Step Checklist

When adding `SubscriptionEntity` (or any new `@Entity`) to an existing `AppDatabase`:

```kotlin
// Before — version 3, two entities
@Database(entities = [ServerEntity::class, GroupEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase()

// After — version 4, three entities
@Database(
    entities = [ServerEntity::class, GroupEntity::class, SubscriptionEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao  // also add the DAO accessor
}
```

If `exportSchema = false` is set, Room does not enforce schema export but still requires a version bump. Without the bump, Room detects schema drift and throws `IllegalStateException: Room cannot verify the data integrity`.

### Discovery in engine-singbox

During P4 (subscription management) for `engine-singbox`, `SubscriptionEntity` was created and its DAO wired up, but `AppDatabase` was not updated. The compile-time step passed cleanly. Only at runtime (or via an instrumentation test) would the crash appear. The version was bumped from 3 → 4 and `subscriptionDao()` accessor was added.

### DAO Accessor Pattern

Every entity added to `@Database` requires a corresponding abstract accessor function:

```kotlin
abstract fun subscriptionDao(): SubscriptionDao
```

Without this, the DAO cannot be obtained from the database instance and Hilt `@Provides` injection of the DAO will fail.

## Related Concepts

- [[concepts/robolectric-room-migration-testing]] - Testing Room migrations with Robolectric
- [[concepts/kapt-per-module-requirement]] - Room annotation processor must be declared per module
- [[concepts/hilt-cross-process-injection]] - AppDatabase from app/ process is unavailable in VPN process — don't inject across process boundary

## Sources

- [[daily/2026-05-24.md]] — Session 19:13: SubscriptionEntity added to engine-singbox but AppDatabase not updated; compile passed, runtime crash expected; fixed by adding entity to @Database entities list, bumping version 3→4, adding subscriptionDao() accessor
