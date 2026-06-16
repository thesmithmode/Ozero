---
title: "Android XML String Escaping Traps"
aliases: [android-strings-apostrophe, xml-string-escaping, strings-xml-apostrophe]
tags: [android, localization, xml, gotcha, release]
sources:
  - "daily/2026-05-06.md"
created: 2026-05-06
updated: 2026-06-12
---

# Android XML String Escaping Traps

Android `strings.xml` requires specific escaping for characters that are valid in UI text but problematic in XML. The two most common traps in multi-locale projects are unescaped apostrophes and invalid Unicode escape sequences. Both cause `release.yml` to fail at the APK build step — they do not produce a lint warning in CI but break the Android resource compiler (`aapt2`) during release builds.

## Key Points

- Apostrophes in string values must be escaped as `\'` or wrapped in double quotes: `"d\'autres"` or `"\"d'autres\""`
- Most common in European locales (fr, es, pt) where apostrophes appear mid-word: `d'autres` (fr), `d'acuerdo` (es), `d'acordo` (pt)
- Invalid Unicode escapes (e.g., `\u` without 4 hex digits) in XML values are accepted by XML parsers but rejected by `aapt2` at build time
- Both errors appear as CI-green/release-red: `ci.yml` does not build a release APK, so `aapt2` errors are invisible until `release.yml` runs
- `values-en/strings.xml` rarely has apostrophes; problems appear when adding fr/es/pt translations without reviewing each word

## Details

### The Apostrophe Rule

Android's resource compiler processes `strings.xml` values through a second pass after XML parsing, applying Android-specific string formatting rules. In this pass, an unescaped apostrophe is interpreted as a delimiter in certain contexts — though the exact behavior depends on whether the string contains format specifiers. The safe rule: always escape apostrophes with a backslash in Android string resources.

```xml
<!-- BROKEN -->
<string name="about_description">Сделано с любовью — pour d'autres utilisateurs</string>

<!-- CORRECT -->
<string name="about_description">Сделано с любовью — pour d\'autres utilisateurs</string>
```

This is particularly insidious when strings are written by native speakers of the target language who include apostrophes naturally, or when translations are copied from a web source without reviewing for Android resource compatibility.

### CI vs Release Visibility

`ci.yml` runs `assembleDebug` for compilation checks. Android's debug build pipeline is more lenient — some resource formatting errors that `aapt2` would reject in release mode are tolerated in debug mode. `release.yml` runs `assembleRelease` with full R8 processing and strict `aapt2` resource validation. This creates a class of errors that are:

- Not flagged by lint (Android Lint does check some apostrophe cases, but not all)
- Not caught by CI (debug build succeeds)
- Only visible in `release.yml` (release build fails with aapt2 error)

The v0.0.4 release was blocked by an unescaped apostrophe in `values-fr/strings.xml:about_description` (`d'autres`). CI was green; `release.yml` failed immediately at the resource compilation step.

### Unicode Escape Trap

A related trap: `\u` followed by fewer than 4 hex digits is not a valid XML character reference. Android's XML parser may accept it (treating `\u` as literal characters), but `aapt2` may reject it depending on context. The pattern appears when Unicode characters are manually entered using `\u` notation incorrectly (e.g., `\u2019` is correct for `'` RIGHT SINGLE QUOTATION MARK, but `\u201` or `\u9` are invalid).

### Prevention

For any localization addition:

1. Run `./gradlew lintRelease` locally before push — catches most apostrophe issues
2. Review translated strings word-by-word for languages with frequent apostrophes (fr, es, pt, it)
3. Prefer Unicode curly apostrophes (`'`) over straight apostrophes (`'`) for display text — they do not require escaping in Android XML

## Related Concepts

- [[connections/release-checks-beyond-ci]] - Android XML escaping errors are another example of release.yml catching defects invisible to CI
- [[concepts/ci-workflow-discipline]] - CI's debug-only build is insufficient for release validation
- [[concepts/release-process]] - Release tagging flow that triggers release.yml
- [[concepts/todo-before-autonomous-work-contract]] - Release tasks need explicit scope because CI-green and release-red states can coexist

## Sources

- [[daily/2026-05-06.md]] - Session 15:01: release.yml red on v0.0.4 due to unescaped apostrophe `d'autres` in values-fr/strings.xml:about_description + invalid unicode escape; CI was green; lesson = apostrophe escaping in fr/es/pt locales
