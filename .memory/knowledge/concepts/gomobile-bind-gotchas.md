---
title: "gomobile bind Gotchas for Android AAR Generation"
aliases: [gomobile-gotchas, gomobile-aar, go-android-binding]
tags: [go, android, native, gomobile, build]
sources:
  - "daily/2026-05-01.md"
created: 2026-05-01
updated: 2026-05-01
---

# gomobile bind Gotchas for Android AAR Generation

`gomobile bind` generates Java/Kotlin wrapper classes from exported Go types, producing an AAR for Android consumption. Several non-obvious behaviors cause silent failures or incomplete bindings: only exported types generate wrappers, non-ASCII in Go source breaks javac, and thin wrapper packages may export less than expected.

## Key Points

- `gomobile bind` generates Java wrappers **only for exported Go types** (capitalized names) in the target package — unexported types are invisible to the AAR consumer
- Non-ASCII characters in Go source comments break `javac` during the AAR packaging step — gomobile generates intermediate `.java` files that inherit the Go source encoding
- Thin wrapper packages (a Go package that imports and re-exports from another) may export only a subset — typically just a `Version()` function if that's the only exported symbol the wrapper defines
- Binding directly against the SDK package (not a wrapper) produces the complete API surface
- `gomobile bind` dependency: `golang.org/x/mobile/cmd/gomobile` + `golang.org/x/mobile/bind` must both be available; missing `bind` causes a cryptic build failure

## Details

### Exported Types Only

`gomobile bind` inspects the Go package's exported API surface using Go's type system. Only types, functions, and methods with capitalized names are included. This is standard Go visibility, but the gotcha is at the **package level**: if you bind package `A` that imports package `B`, only `A`'s direct exports appear in the AAR. `B`'s types are not transitively exported unless `A` explicitly re-exports them as its own API.

During the URnetwork SDK integration, a thin wrapper package was created to import `github.com/urnetwork/sdk`. The wrapper only defined `func Version() string`, so the generated AAR contained only a `Version` class — none of the SDK's actual types (client, auth, network management) were accessible. The fix was to bind directly against the SDK package itself.

### Non-ASCII Encoding Trap

`gomobile bind` generates intermediate Java source files from Go type signatures. When Go source files contain non-ASCII characters (common in comments for Russian-language projects), the generated Java files may inherit encoding issues. `javac` defaults to the platform encoding and fails when it encounters unexpected byte sequences.

The URnetwork SDK build failed on the third iteration due to non-ASCII comments in the Go source. Solutions include: stripping non-ASCII from Go source before binding, setting `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8` in the build environment, or ensuring the Go source uses ASCII-only comments.

### Dependency Resolution

`gomobile bind` requires both the `gomobile` command and the `golang.org/x/mobile/bind` package. The first build iteration of the URnetwork SDK failed because `bind` was not in the module's dependency graph. `go get golang.org/x/mobile/bind` must be run before `gomobile bind`, or the bind package must be listed in `go.mod`.

### Build Pipeline Pattern

The successful URnetwork SDK AAR build followed this sequence:

1. Clone SDK repo at pinned version
2. `go get golang.org/x/mobile/bind` (ensure dependency)
3. `gomobile init`
4. `gomobile bind -target android/arm64,android/arm,android/amd64 -androidapi 24 ./sdk/...` (bind directly against SDK package)
5. Verify generated AAR contains expected classes via `jar tf` or Android Studio inspection

## Related Concepts

- [[concepts/xray-aar-build-research]] - Same gomobile bind pipeline for Xray-core AAR, same dependency and cache patterns
- [[concepts/urnetwork-sdk-integration]] - URnetwork SDK was the context where these gotchas were discovered

## Sources

- [[daily/2026-05-01.md]] - 4 build iterations of URnetwork SDK AAR: (1) gomobile/bind dep miss, (2) wrapper exported only Version, (3) javac encoding fail on non-ASCII, (4) success binding directly against SDK package
