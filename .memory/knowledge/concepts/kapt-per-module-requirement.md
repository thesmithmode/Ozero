---
title: "kapt Annotation Processor Must Be Declared Per Module"
aliases: [kapt-module-requirement, hilt-compiler-per-module, room-compiler-per-module]
tags: [kapt, hilt, room, gradle, android, annotation-processing]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# kapt Annotation Processor Must Be Declared Per Module

Every Gradle module that uses Hilt (`@HiltViewModel`, `@AndroidEntryPoint`, `@Inject`) or Room (`@Dao`, `@Entity`, `@Database`) must explicitly declare the corresponding annotation processor via `kapt`. Annotation processors are not transitive — declaring `kapt(libs.hilt.compiler)` in `:app` does not enable Hilt code generation in `:engine-singbox`.

## Key Points

- `kapt(libs.hilt.compiler)` is required in every module that uses `@HiltViewModel`, `@AndroidEntryPoint`, or `@InstallIn`
- `kapt(libs.room.compiler)` is required in every module that declares `@Dao`, `@Database`, or `@Entity`
- The `implementation` dependency on Hilt/Room is separate from the `kapt` processor — both are needed
- Missing kapt causes silent failure or cryptic "None of the following candidates is applicable" errors at compile time
- First Hilt engine module in Ozero with direct Hilt usage was `engine-singbox` — prior engine modules didn't use Hilt directly

## Details

### Discovery

During `engine-singbox` implementation, the module used `@HiltViewModel`, `@AndroidEntryPoint`, and `@InstallIn` annotations. The `build.gradle.kts` included `implementation(libs.hilt.android)` but was missing `kapt(libs.hilt.compiler)`. CI failed with Hilt-related errors. Adding `kapt(libs.hilt.compiler)` resolved the annotation processing issue.

The same applies to Room: `engine-singbox` declared `@Dao` and `@Database` and required `kapt(libs.room.compiler)` explicitly in its `build.gradle.kts`.

### Template for Engine Modules Using Hilt + Room

```kotlin
// engine-singbox/build.gradle.kts
dependencies {
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)          // REQUIRED — not transitive from :app

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)          // REQUIRED — not transitive from :app
}
```

### Why Engines Previously Didn't Need This

Earlier engine modules (`engine-warp`, `engine-byedpi`, `engine-masterdns`) did not use Hilt annotations directly in their module code — they relied on the `:app` module's Hilt component graph for DI. `engine-singbox` was the first to declare its own `@AndroidEntryPoint` service and `@HiltViewModel` classes within the engine module itself.

## Related Concepts

- [[concepts/hilt-cross-process-injection]] - VPN process has a separate Hilt SingletonComponent; modules must still declare kapt
- [[concepts/hilt-assistedinject-mixed-injection]] - Other Hilt edge cases in Ozero
- [[concepts/ci-gradle-log-reading]] - How to read the actual Hilt/kapt errors from CI

## Sources

- [[daily/2026-05-24.md]] — Session 19:13: engine-singbox CI failures traced to missing `kapt(libs.hilt.compiler)` in engine-singbox/build.gradle.kts; first engine module to use Hilt annotations directly within the module
