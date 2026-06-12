---
title: "About Screen Purpose Minimization"
sources:
  - "daily/2026-05-22.md"
created: 2026-06-12
updated: 2026-06-12
---

# About Screen Purpose Minimization

Ozero's About screen should avoid describing the app as a VPN or circumvention tool when that description creates unnecessary distribution or legal risk. The durable product rule is to show only low-risk metadata such as version information unless a specific release requirement demands more.

## Key Points

- `about_description` was removed from all locale resources instead of rewritten.
- The About screen should not explain sensitive app purpose when the UI does not need that text.
- Removing the string across locales avoids inconsistent translations that preserve the risky description.
- This is a product/privacy surface decision, not a localization-only cleanup.

## Details

The user identified that the About description exposed the app purpose too directly. The accepted fix was to delete `about_description` from every locale rather than replace it with a softer phrase. Keeping only version metadata reduces unnecessary disclosure while preserving the screen's practical purpose.

This fits the public-repo and sensitive-data discipline used elsewhere in Ozero: user-facing and repository-visible text should be reviewed for what it reveals to third parties. If text is not required for operation or support, the safest path is removal rather than euphemistic rewriting.

## Related Concepts

- [[concepts/public-repo-secret-and-insecure-asset-boundary]] - Public artifacts must be reviewed for what outsiders can learn from them.
- [[concepts/prod-log-infra-redaction]] - Production-visible diagnostics should avoid unnecessary sensitive disclosure.
- [[concepts/repo-debranding-ci-owner-injection]] - User-specific or sensitive identifiers should be removed from public surfaces.

## Sources

- [[daily/2026-05-22]] - Session 19:25: user flagged About description as a legal-risk disclosure; `about_description` was removed from ru/en/es/pt/de/fr/ja/hi/zh-rCN/ar rather than replaced.
