---
title: WARP register error hint and parser boundary
sources:
  - daily/2026-05-21.md
created: 2026-06-12
updated: 2026-06-12
---
# WARP register error hint and parser boundary

## Key Points
- WARP auto-registration failures should show a user-actionable hint, not raw network errors.
- The Info flow with generator sites is the fallback UX when automatic generation cannot reach the service.
- `WarpConfParser.extractHex` should accept explicit hex payloads, not bare decimal values accidentally parsed as payload bytes.
- Locale parity must be kept across `values`, `values-en`, `values-es`, and `values-pt` for new WARP UI strings.

## Details

The WARP generation error screen previously exposed raw connection failures such as inability to connect to a generator host. The fix changed the user-facing path to a localized hint telling the user to open the Info menu, use a site generator, download a `.conf`, and import it manually. The Info button and generator sites already existed, so the fix was the error-to-action mapping rather than adding the whole fallback surface.

The same batch fixed `WarpConfParser.extractHex`: bare values such as `13` or `41` must remain normal numeric fields like `I3 = 41`, while payload bytes require explicit `<b 0x..>` or `0x..` markers. Without that boundary, the builder could round-trip `I*` values through binary payload formatting and parse them back to defaults.

## Related Concepts
- [[concepts/warp-config-generator-api]]
- [[concepts/warp-config-import-naming-dedup]]
- [[concepts/android-xml-string-escaping]]
- [[concepts/release-runtime-scenario-checklist]]

## Sources
- [[daily/2026-05-21.md]] records the WARP register UX fix from raw connection error to localized manual-import hint.
- [[daily/2026-05-21.md]] records the `extractHex` fix requiring explicit hex markers and the four-locale parity updates.
