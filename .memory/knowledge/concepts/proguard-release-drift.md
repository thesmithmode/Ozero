---
title: "Proguard/R8 Release Drift: JDK-Only Dependencies on Android"
aliases: [proguard-release-drift, r8-minify-drift, r8-jdk-class-crash]
tags: [android, r8, proguard, dependency, release, security]
sources:
  - "daily/2026-05-23.md"
created: 2026-05-23
updated: 2026-06-12
---

# Proguard/R8 Release Drift: JDK-Only Dependencies on Android

R8 minification in release builds catches class references that are absent on Android but present on desktop JDK. These failures do not appear in debug CI because debug builds skip R8 minification. The pattern manifests as a `ClassNotFoundException` at runtime or a build failure with `Missing class` during release APK assembly.

## Key Points

- Debug CI uses `minifyEnabled false` → R8 not invoked → JDK-only references pass silently
- Release APK uses `minifyEnabled true` + `shrinkResources true` → R8 traces every reference
- Any new dependency with JCE, EdDSA, or JDK cryptography must be audited for `sun.*` / `javax.security.*` imports before merge
- Fix pattern: `dontwarn` in `proguard-rules.pro` for the offending JDK-internal package
- Three confirmed instances in Ozero: FPTN `c++_static` (linker, not R8), sshj `EdDSA`, and Bouncy Castle internal refs

## Details

### Mechanism

R8 processes the full classpath including transitive dependencies. If a library on the classpath contains an `import sun.security.x509.X509Key` (or similar JDK internal), R8 reports a missing class error:

```
Missing class sun.security.x509.X509Key
```

On desktop JDK this class exists in `rt.jar`. Android has no `rt.jar` — the class is absent. Debug builds that skip R8 never encounter this.

### sshj → EdDSA → sun.security (2026-05-23)

Adding `com.hierynomus:sshj` for MasterDNS SSH deploy introduced the chain:

```
sshj → net.i2p.crypto:eddsa → EdDSAEngine.java → import sun.security.x509.X509Key
```

Fix added to `proguard-rules.pro`:
```
-dontwarn sun.security.x509.**
-dontwarn sun.security.**
```

The `sun.security` import in `EdDSAEngine` is dead code on Android (the conditional branch that uses `X509Key` never executes), but R8 does not perform dead-code-sensitive class checking — it traces all imports.

### General Guard Pattern

Before merging any dependency that includes:
- `net.i2p.crypto` (EdDSA)
- `javax.crypto` (JCE)
- `java.security` (JCA)
- `org.bouncycastle` (BC)
- Any library calling `Class.forName("sun.*")`

Run: `unzip -p dep.aar classes.jar | javap -c | grep sun\.security` to surface JDK-internal references early.

Alternatively: add a release CI check step that runs `./gradlew assembleRelease` (not just `assembleDebug`) on any PR touching `build.gradle.kts` dependency declarations.

## Related Concepts

- [[concepts/masterdns-deploy-hardening]] — sshj R8 crash was the first instance caught; Phase C of hardening
- [[concepts/release-stub-gate]] — `release.yml` has independent validation gates beyond CI; same motivation
- [[concepts/release-process]] — Release APK uses R8; debug CI does not; gap between CI green and release success
- [[concepts/android-ndk-cxx-static-linking]] — FPTN `c++_static` is the linker analog of R8 drift (dependency linked wrong → crash on device but not on host)

## Sources

- [[daily/2026-05-23.md]] — Session 17:25: sshj dep pulled `net.i2p.crypto:eddsa` → `EdDSAEngine` references `sun.security.x509.X509Key` (JDK-only) → R8 minify crash at Android startup; fix `dontwarn sun.security.**` in proguard-rules.pro; sentinel added to verify rule presence before release
