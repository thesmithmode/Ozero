---
title: Source mojibake diagnostics risk
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Source mojibake diagnostics risk

## Key Points
- Mojibake in Kotlin source or tests degrades diagnostics, review quality, and runtime log usefulness.
- Encoding-sensitive edits should use stable ASCII anchors and avoid rewriting unrelated Unicode strings.
- Workflow and source rewrites need extra care because tools can silently introduce BOM or corrupt text.
- This risk is related to [[concepts/android-xml-string-escaping]] and [[concepts/kotlin-import-at-file-level-only]] as a source-level correctness trap.

## Details

Several 2026-05-29 sessions recorded mojibake in logs and modified Kotlin source/test strings. The immediate risk was not only readability: corrupted diagnostics make it harder to match runtime traces, CI failures, and user-visible states to the code path that produced them.

The same day also recorded a workflow-editing hazard: `Set-Content -Encoding UTF8` can add BOM or otherwise disturb Unicode-sensitive files. For this repository, edits that touch localized text, diagnostics, workflow files, or tests should be scoped to stable ASCII anchors and reviewed for encoding damage before commit.

## Related Concepts
- [[concepts/android-xml-string-escaping]]
- [[concepts/kotlin-import-at-file-level-only]]
- [[concepts/ci-required-check-name-preservation]]
- [[concepts/regression-diagnostics-real-path-grounding]]

## Sources
- [[daily/2026-05-29]] records mojibake in changed Kotlin source/test files as a review and diagnostics problem.
- [[daily/2026-05-29]] records that workflow editing with `Set-Content -Encoding UTF8` can add BOM or corrupt Unicode-sensitive content.
