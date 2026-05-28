---
title: Detekt TooManyFunctions in objects can be fixed with private top-level helpers
sources:
  - daily/2026-05-27.md
created: 2026-05-28
updated: 2026-05-28
---
# Detekt Object Function Extraction

## Key Points
- Detekt TooManyFunctions on Kotlin object counts private builder helpers inside the object.
- Pure helper functions can move to private top-level functions when they do not need object state.
- Moving many functions changes indentation; ktlint cleanup must be expected.
- The refactor is structural, not behavioral, when helper visibility remains private to the file.

## Details

ConfigBuilder exceeded the TooManyFunctions threshold with 21 functions. The fix moved eight stateless helpers to private top-level functions: vless/vmess/trojan/ss outbound builders, buildTransport, buildTls, buildMap, and jsonString. That lowered the object function count to 13.

This pattern is useful for Kotlin singleton builders that accumulate parsing and JSON helper methods. The owning file remains the boundary, while the object keeps only stateful or public orchestration behavior.

## Related Concepts
- [[concepts/detekt-toomany-functions-semantics]]
- [[concepts/ktlint-brace-symmetry]]
- [[concepts/singbox-subscription-architecture]]

## Sources
- [[daily/2026-05-27.md]]: Session 11:42 recorded ConfigBuilder TooManyFunctions 21 to 13 by moving eight helpers to private top-level functions.
