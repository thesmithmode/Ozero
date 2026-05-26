---
title: "gomobile Build: go get Must Add to require, Not tool"
aliases: [gomobile-build, go-get-tool-vs-require, gomobile-ci-fix]
tags: [gomobile, go, ci, build]
sources:
  - "daily/2026-05-25.md"
created: 2026-05-25
updated: 2026-05-25
---

# gomobile Build: go get Must Add to require, Not tool

When building gomobile for Android in CI, `go get -tool golang.org/x/mobile/cmd/gobind@VERSION` adds the dependency to the `tool` directive in `go.mod`, not to `require`. gomobile checks for the package in `require` at runtime — if it's only in `tool`, gomobile fails with a "cannot find module" error.

## Key Points

- `go get -tool <pkg>` adds to `go.mod [tool]` section — gomobile does NOT look there
- `gomobile` requires `golang.org/x/mobile` in the `[require]` section of `go.mod`
- Correct command: `GOFLAGS='-mod=mod' go get "golang.org/x/mobile@latest"` — adds to `require`
- Go version matters: `golang.org/x/mobile@latest` required Go 1.25; workflow was on 1.24 → `GOTOOLCHAIN` auto-switched to 1.25.10 before the fix
- Version pinned to `latest` ensures `require` entry; explicit version tags also work

## Details

### CI Failure Chain (5 Iterations)

The `build-singbox.yml` workflow went through 5 iterations to produce a working gomobile build:

- **Runs 1-3**: Various `go get golang.org/x/mobile@latest` approaches in wrong working directory or with wrong flags
- **Run 4**: `go get -tool golang.org/x/mobile/cmd/gobind@$MOBILE_VERSION` — this adds to `[tool]` section in go.mod. gomobile then fails because it checks `[require]` for the package presence. Additionally, the workflow was using Go 1.24 but `golang.org/x/mobile@latest` required Go 1.25; Go's `GOTOOLCHAIN=auto` transparently upgraded to 1.25.10 (not the issue, but explains the version mismatch in logs)
- **Run 5 (fix)**:
  1. Workflow `go-version` changed from `'1.24'` to `'1.25'`
  2. Script's version-detection block replaced with: `GOFLAGS='-mod=mod' go get "golang.org/x/mobile@latest"` — this unconditionally adds to `require`

### `GOFLAGS='-mod=mod'`

Without `-mod=mod`, Go may refuse to modify `go.mod` in certain module states (e.g., when `go.sum` would need updating). `-mod=mod` allows the go command to update both `go.mod` and `go.sum`.

### Result

After run 5, `build-singbox.yml` produced a valid `libbox.aar` artifact published as a GitHub release with tag `singbox-{hash}`. CI workflows in `dev` download this AAR to enable the singbox module.

## Related Concepts

- [[concepts/singbox-engine-design]] - Context for why libbox.aar is needed
- [[concepts/gomobile-bind-gotchas]] - Other gomobile build traps
- [[concepts/ci-workflow-discipline]] - CI iteration discipline during AAR build debugging

## Sources

- [[daily/2026-05-25.md]] — `build-singbox.yml` 5-iteration fix: `go get -tool` adds to `[tool]` not `[require]`; gomobile checks `require`; fix = `GOFLAGS='-mod=mod' go get golang.org/x/mobile@latest`; Go version bumped 1.24→1.25 for x/mobile@latest compatibility
