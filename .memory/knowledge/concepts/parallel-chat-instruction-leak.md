---
title: "Parallel Chat Instruction Leak"
aliases: [cross-chat-contamination, wrong-chat-instruction, multi-session-context-bleed]
tags: [process, workflow, ai-collab, planning]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Parallel Chat Instruction Leak

When a user maintains multiple AI chat sessions simultaneously, an instruction typed in one chat can accidentally be sent into a different chat. The receiving assistant treats the instruction as authoritative for its current task, potentially reverting deliberate design decisions. The pattern is insidious because the instruction is syntactically valid and plausible — only semantic context reveals it belongs elsewhere.

## Key Points

- User with multiple open chat sessions may mis-direct a message (wrong tab/window) and the assistant has no way to detect this
- The leaking instruction typically contradicts a prior decision that was made deliberately in the receiving session
- Contradiction with recent history is the primary signal: if an instruction reverses a recently-agreed design point, verify its context before acting
- Ask "does this instruction refer to the current task, or could it have been intended for another context?" — one clarifying question prevents hours of rework
- After receiving a "don't do X" instruction: check if X was recently established in this session; if yes, flag the potential mismatch before acting

## Details

### Incident (2026-05-24)

During sing-box engine planning, the user sent the message "конфиги как сейчас, бэкенд не наша забота" (configs as now, backend is not our concern) with intent to remove preset subscription groups from the plan (AD-14). The assistant acted on this, stripping `preset_groups.json`, `SubscriptionBean.isBuiltin`, `GroupSeeder`, and sentinel tests from the plan.

Later in the session (17:09), the user clarified: that message had been sent to the sing-box chat by mistake — it was written for the WARP engine context, where presets genuinely are not Ozero's responsibility. For sing-box, the preset groups from КИБЕРЩИТ-X were a deliberate, agreed design decision.

The result: a full plan revision (v1.2) was required to restore AD-14 and the removed components.

### Detection Heuristic

A leaking instruction typically has one or more of these properties:
- It contradicts a design decision made in the same session within the last few exchanges
- It uses vague or context-free phrasing ("как сейчас" = "as now") that only makes sense in another system's context
- It reverses a significant architectural element without justification

### Mitigation

For significant plan reversals (removing a component that was explicitly discussed and agreed): add one clarifying question before acting. The cost of asking is low; the cost of re-doing the plan is high. The CLAUDE.md principle "minimize questions" applies to implementation details, not to instructions that contradict recently finalized design decisions.

## Related Concepts

- [[concepts/singbox-engine-design]] - The plan that was affected; preset_groups (AD-14) were incorrectly removed then restored
- [[concepts/ci-workflow-discipline]] - Example of how session context discipline prevents compounding errors

## Sources

- [[daily/2026-05-24.md]] — Session 17:09: user clarified that "конфиги как сейчас, бэкенд не наша забота" was intended for the WARP chat, not sing-box; AD-14 preset groups restored in plan v1.2; lesson: contradictory instruction that reverses a recent decision warrants a context-verification question before acting
