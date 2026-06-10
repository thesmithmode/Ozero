---
title: "TOML Windows Path Escaping Trap"
aliases: [toml-backslash-escape, toml-windows-paths, codex-toml-trap]
tags: [toml, config, windows, gotcha]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# TOML Windows Path Escaping Trap

TOML double-quoted strings treat `\` as an escape character. Windows paths use `\` as a separator — writing them in a double-quoted TOML value without escaping each backslash produces a parse error or silently incorrect paths. The fix is to use TOML single-quoted literal strings (`'...'`), where backslashes have no special meaning.

## Key Points

- TOML double-quoted strings: `\` is escape character → `C:\path\to\file` must be `C:\\path\\to\\file`
- TOML single-quoted literal strings: `\` is literal → `'C:\path\to\file'` works as-is
- Automated agents (Codex, scripts) editing TOML often insert Windows paths in double-quotes → instant parse error on next startup
- Symptom: config file fails to parse at startup with a cryptic TOML error referencing the path line
- Risk: any automated TOML editor that doesn't distinguish quote type will corrupt the file

## Details

### The Incident

Codex agent added a JetBrains MCP entry to `~/.codex/config.toml`. The entry included Windows-style paths like `<windows-user>\...\jetbrains-mcp-proxy.exe` written in double-quoted TOML strings. TOML requires all backslashes in double-quoted strings to be escaped as `\\`, so the unescaped path produced a parse error and Codex failed to start.

### TOML Quote Types

```toml
# BROKEN: double-quoted string, backslash is escape char
command = "<local-programs>/jetbrains-mcp-proxy.exe"

# FIXED: single-quoted literal string, backslash is literal
command = '<local-programs>/jetbrains-mcp-proxy.exe'
```

For any Windows path in TOML, single-quoted literals are always safe. Double-quoted strings require `\\` for every separator:
```toml
command = "<local-programs>/jetbrains-mcp-proxy.exe"
```

### Prevention for Automated Agents

When any automated process (CI script, LLM agent, code generator) writes or modifies a TOML file containing Windows paths:
1. Prefer single-quoted literals `'...'` for any string containing backslashes
2. After automated edits: validate the file by parsing it before use
3. For `~/.codex/config.toml` specifically: verify quote types in `mcpServers` entries after any automated change

## Related Concepts

- [[concepts/ci-workflow-discipline]] - Automated changes that require manual verification before taking effect
- [[concepts/android-build-dockerfile-env-trap]] - Similar class of env/config propagation trap where automation produces incorrect config

## Sources

- [[daily/2026-05-24.md]] — Session 15:14: Codex added JetBrains MCP to `~/.codex/config.toml` with unescaped Windows paths in double-quoted TOML strings → parse error; fix = replace with single-quoted literals
