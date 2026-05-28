---
title: Detekt ReturnCount is reduced by extracting branch paths
sources:
  - daily/2026-05-27.md
created: 2026-05-28
updated: 2026-05-28
---
# Detekt ReturnCount Extract Paths

## Key Points
- Detekt ReturnCount counts early guard-clause returns, not only business-logic returns.
- A start method with many guards can exceed the threshold even when behavior is simple.
- Convert guard-heavy flows to a when-expression and extract branch-specific paths.
- Prefer preserving branch semantics over suppressing ReturnCount.

## Details

In SingboxEngine.start(), ReturnCount was reduced from 10 to 4 by changing the chain guard flow into a when-expression and extracting the auto-select path. The refactor kept lifecycle behavior intact while making the start path shape explicit enough for detekt.

The reusable pattern is to keep the public lifecycle method as an orchestrator and move concrete branches into private helpers. This avoids threshold churn and keeps start/stop lifecycle decisions local to the owning engine module.

## Related Concepts
- [[concepts/cyclomatic-complexity-extract-helper]]
- [[concepts/detekt-toomany-functions-semantics]]
- [[concepts/singbox-engine-design]]

## Sources
- [[daily/2026-05-27.md]]: Session 11:42 recorded SingboxEngine.start() ReturnCount 10 to 4 via when-expression and extracted auto-select path.
