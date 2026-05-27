---
title: "CI Heredoc Single-Quote Variable Trap"
aliases: [heredoc-no-expand, single-quote-heredoc, shell-heredoc-variable]
tags: [ci, shell, bash, github-actions, debian]
sources:
  - "daily/2026-05-27.md"
created: 2026-05-27
updated: 2026-05-27
---

# CI Heredoc Single-Quote Variable Trap

In shell (bash/sh), a heredoc with a single-quoted delimiter (`<< 'EOF'` or `<< 'CONTROL'`) suppresses all variable expansion inside the block. Every `$VAR` is treated as a literal string. When used in GitHub Actions `run:` steps to generate config files, this silently produces files containing the literal text `$VERSION` instead of the resolved value — with no warning or error at assignment time. The error surfaces only when a downstream tool (like `dpkg-deb`) tries to parse the file and finds an empty or invalid field.

## Key Points

- `<< 'HEREDOC'` — variables NOT expanded; literal `$VAR` in output
- `<< HEREDOC` (no quotes) — variables expanded normally
- `<< "HEREDOC"` — same as unquoted; variables expanded
- The failure manifests far from the cause: `dpkg-deb: error: Version string is empty` despite the string `0.2.11` appearing in the error message itself (it was being read from the field name context, not the value)
- Affects any generated config file: `dpkg control`, `spec` files, `PKGBUILD`, `Makefile` fragments

## Details

The trap is triggered when a CI developer writes a heredoc block to generate a Debian `control` file, using single-quotes to avoid shell metacharacter issues in the file content. The block looks correct syntactically and passes YAML linting. The shell script runs without error. The `control` file is created. But its content has `Version: $VERSION` (literal) instead of `Version: 0.2.11`.

When `dpkg-deb --build` subsequently reads this file, it parses `Version: ` as an empty value because `$VERSION` is not a valid Debian version string. The error message `error in Version string "0.2.11"` is particularly misleading — the `0.2.11` in the error comes from the package name context or surrounding text, not the Version field. This leads investigators to conclude the VERSION env variable is set correctly (it is) and look for other causes, burning significant debugging time.

The fix is trivial: remove quotes from the heredoc delimiter. `<< 'CONTROL'` → `<< CONTROL`. However the correct delimiter must be targeted; if multiple heredoc blocks exist in the same CI file, each must be checked independently. Changing `<< 'EOF'` in one block does not fix `<< 'CONTROL'` in another.

In GitHub Actions, the same trap applies to any multi-line `run:` step that uses heredoc to write files. The YAML `|` block scalar is processed by YAML first (stripping leading indentation), then passed to the shell — the single-quote semantics are applied at shell evaluation time, not YAML parse time.

## Related Concepts

- [[concepts/release-process]] - deb packaging is part of the Linux release artifact build
- [[concepts/ci-workflow-discipline]] - shell idioms in CI require the same rigor as production code
- [[concepts/toml-windows-path-escaping-trap]] - similar class of "config file encoding trap" in a different format

## Sources

- [[daily/2026-05-27.md]] - `<< 'CONTROL'` in deb packaging step blocked `$VERSION` expansion → `dpkg-deb: error: Version string is empty`; fix: `<< CONTROL`; root cause discovered after 7 failed release pipeline runs
