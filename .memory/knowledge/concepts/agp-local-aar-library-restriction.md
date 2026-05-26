---
title: "AGP Prohibits Local AAR Dependency in Library Modules"
aliases: [agp-aar-library, local-aar-library-module]
tags: [android, gradle, agp, aar]
sources:
  - "daily/2026-05-25.md"
created: 2026-05-25
updated: 2026-05-25
---

# AGP Prohibits Local AAR Dependency in Library Modules

Android Gradle Plugin (AGP) does not allow a library (AAR) module to declare a local `.aar` file as a dependency. Only `application` modules can depend on local `.aar` files. This restriction means any library module that needs gomobile-generated AAR code must use an alternative approach.

## Key Points

- `implementation(files("libs/libbox.aar"))` in an AAR library module → AGP build error
- Application modules can use local `.aar` dependencies; library modules cannot
- Workaround: decompose the AAR in CI into `classes.jar` (jarjar-renamed) + `jniLibs/arm64-v8a/*.so`
- `singbox-core/build.gradle.kts` depends on `*.jar` only; the `.so` file goes to `jniLibs/`
- This workaround is used for `libbox.aar` (sing-box) in the `singbox-core` module

## Details

### The Restriction

AGP enforces this at configuration time with a message similar to: "Direct local .aar file dependencies are not supported when the dependent is an Android library." The rationale is that AAR-to-AAR dependency resolution requires proper Maven coordinates for transitive dependency management.

### Workaround: Decompose in CI

The `build-singbox.yml` workflow decomposes `libbox.aar` before it's consumed:

```bash
# In CI: decompose libbox.aar
unzip libbox.aar -d libbox_extracted
# Rename to avoid DEX class ID conflicts
java -jar jarjar.jar process rules.txt \
    libbox_extracted/classes.jar \
    singbox-core/libs/libbox.jar
# Copy native library
cp libbox_extracted/jni/arm64-v8a/libbox.so \
    engine-singbox/src/main/jniLibs/arm64-v8a/libbox.so
```

The `singbox-core/build.gradle.kts` then references only the jar:
```kotlin
implementation(fileTree("libs") { include("*.jar") })
```

The `jniLibs/` directory is automatically packaged by AGP into the final APK.

### go-stubs Interaction

The `go/*.class` files must also be handled — they are stripped from the main jar (to avoid `go.Seq` conflicts with URnetwork) and provided separately as `go-stubs.jar` with `compileOnly` or `implementation` depending on whether they're needed at runtime. See [[concepts/gomobile-go-seq-multi-sdk-conflict]] and [[concepts/compileonly-nontransitive-android-library]].

## Related Concepts

- [[concepts/gomobile-go-seq-multi-sdk-conflict]] - Why the AAR decomposition must also handle go.* classes
- [[concepts/compileonly-nontransitive-android-library]] - go-stubs compileOnly non-transitivity issue
- [[concepts/singbox-engine-design]] - Context: singbox-core module architecture
- [[concepts/extract-native-libs-legacy-packaging]] - Related native lib packaging constraints

## Sources

- [[daily/2026-05-25.md]] — AGP restriction: local .aar dependency forbidden in library module; singbox-core is a library → libbox.aar cannot be referenced directly; workaround: CI decomposes AAR to jar + jniLibs; singbox-core depends on *.jar only
