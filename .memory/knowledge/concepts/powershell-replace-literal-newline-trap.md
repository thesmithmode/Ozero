---
title: PowerShell replace can insert literal newline escape text
sources:
  - daily/2026-06-03.md
created: 2026-06-13
updated: 2026-06-13
---
# PowerShell replace can insert literal newline escape text
## Key Points
- Careless PowerShell `-replace` edits can write literal `` `r`n `` or `\`r\`n` text into Kotlin files.
- Literal newline escape text can turn a formatting fix into a syntax or lint failure.
- After multiline PowerShell replacements, inspect the file and staged diff for escape-sequence garbage.
- This is distinct from PowerShell heredoc syntax traps but belongs to the same shell-edit risk family.
## Details
The daily log records repeated cases where PowerShell replacement commands risked inserting literal newline sequences into Kotlin files while trying to normalize multiline lambdas or formatting. In Ozero, that matters because local Gradle/lint validation is prohibited, so a malformed text edit may only surface in CI unless the diff is inspected carefully.

The mitigation is cheap and mechanical: after any PowerShell multiline replacement, search or review for literal escape sequences and inspect the staged diff before committing. This complements [[kotlin-import-at-file-level-only]], which already captures PowerShell heredoc pitfalls, and [[source-mojibake-diagnostics-risk]], which captures encoding-related diagnostic damage.
## Related Concepts
- [[kotlin-import-at-file-level-only]]
- [[source-mojibake-diagnostics-risk]]
- [[ktlint-test-line-length-ci-blocker]]
- [[ci-style-failure-hides-compile-regression]]
## Sources
- `daily/2026-06-03.md`: noted that PowerShell `-replace` could insert literal `` `r`n `` or other garbage into Kotlin files.
- `daily/2026-06-03.md`: required staged diff checks for literal newline sequences after such replacements.
