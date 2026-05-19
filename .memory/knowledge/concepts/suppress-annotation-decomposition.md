---
title: "Suppress Annotation Anti-Pattern: Decompose Instead"
aliases: [suppress-annotation, longmethod-suppress, ai-suppress-pattern]
tags: [code-quality, android, kotlin, anti-pattern, review]
sources:
  - "daily/2026-05-12.md"
  - "daily/2026-05-16.md"
created: 2026-05-12
updated: 2026-05-16
---

# Suppress Annotation Anti-Pattern: Decompose Instead

Adding `@Suppress("LongMethod")` or similar lint/style suppression annotations to silence code quality warnings is an anti-pattern. Suppression hides a real code quality problem without solving it. When a method is too long, the correct fix is decomposition into smaller named components — not annotation suppression. This pattern appears frequently in AI-generated (subagent) code where adding a suppress annotation is the path of least resistance to pass lint.

## Key Points

- `@Suppress("LongMethod")` silences lint but does not reduce complexity — the code remains hard to maintain
- Correct fix for "LongMethod": extract logic into named helper functions or Compose sub-components
- Suppression is legitimate only for known false positives: framework-generated code, test utilities, or platform workarounds with no structural alternative
- Code review must actively catch suppress annotations and require replacement with structural improvements
- AI subagents frequently generate suppress annotations — requires explicit review gate; commit pre-check via `git diff --staged` catches them
- Pattern generalizes: `@Suppress("TooManyFunctions")`, `@Suppress("LargeClass")` all indicate structural issues requiring decomposition, not suppression

## Details

### The Ozero Discovery

In session 2026-05-12 17:55, a subagent refactoring `UrnetworkEngineSettingsScreen.kt` added `@Suppress("LongMethod")` to a Compose settings composable rather than extracting sections. Code review caught this and required proper decomposition: the monolithic settings function was split into `ProvideSection`, `ProvideToggleSection`, and `SectionDivider` components.

The decomposed structure is both lint-compliant and clearer — each section is named, its purpose is self-documenting, and future modifications affect a smaller surface area. The `@Suppress` annotation would have deferred the maintenance cost while hiding the indicator that the code needed attention.

### When Suppression Is Appropriate

Legitimate uses for `@Suppress`:
- Framework entry points that generate complex code (e.g., Hilt `@AndroidEntryPoint`, Room DAOs)
- Test setup methods that are necessarily verbose
- Temporary migration code with a documented plan for removal
- Platform-specific workarounds with no structural alternative

In these cases, add a comment explaining WHY suppression is intentional: `@Suppress("LongMethod") // Hilt entry point — complexity is inherent`. Comments-free `@Suppress` blocks are automatically suspicious.

### Review Gate

Check staged diffs (`git diff --staged`) before every commit for new `@Suppress` annotations. Any new suppress without a justification comment = automatic rejection. This is more reliable than relying on reviewers to notice suppress annotations in large diffs.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI lint gates that suppress annotations attempt to bypass
- [[concepts/sentinel-fqn-desync]] - Similar pattern: test shortcuts that pass without verifying correctness; suppress annotations pass lint without fixing quality
- [[connections/audit-driven-concurrency-discovery]] - Same session (17:55→18:34) where multiple subagent quality issues were caught in review

## Sources

- [[daily/2026-05-12.md]] - Session 17:55: subagent added `@Suppress("LongMethod")` to UrnetworkEngineSettingsScreen; caught in code review; replaced with ProvideSection/ProvideToggleSection/SectionDivider decomposition; rule: decompose instead of suppress
- [[daily/2026-05-16.md]] - Session 15:55: `ExpertMainContent` in MainScreen exceeded 120 lines (detekt LongMethod); badges-block extracted into `ExpertStatusBadges` (11 params, `@Suppress("LongParameterList")` accepted as necessary for Compose component with many state inputs); function reduced to <80 lines
