---
title: "TODO Before Autonomous Work Contract"
aliases: [todo-before-work, autonomous-todo-contract, task-list-before-work]
tags: [workflow, planning, autonomy, user-contract]
sources:
  - "daily/2026-05-06.md"
created: 2026-06-12
updated: 2026-06-12
---

# TODO Before Autonomous Work Contract

For Ozero work sessions, the agent must create and align a concrete TODO list before starting substantial implementation, especially when multiple release tasks, user-owned verification items, and technical investigations are mixed. Starting work on an unlisted task can violate the user's control expectations even if the task is technically relevant.

## Key Points

- Substantial work should begin from an explicit ordered TODO, not from an implicit backlog guess.
- User-owned verification reminders must be labeled as user tasks, not agent tasks.
- Completed TODO items should be cleared promptly instead of accumulating stale release noise.
- If a release has many small fixes, the default is to do the available coherent batch and tag once rather than split artificially.
- The TODO contract complements, but does not replace, the internal `update_plan` discipline.

## Details

The 2026-05-06 log recorded user frustration after the agent started WARP work without first listing the active tasks. The correction was to build a 10-item TODO before continuing. This is a workflow lesson: autonomous investigation is useful only after the active work surface is explicit enough for the user to see what is in scope and what is not.

The same session clarified ownership of WARP physical-device verification. The item stayed as a reminder because real device proof was still needed after `uapiPath`/WARP fixes, but the user stated it was not an agent task. This distinction prevents false accountability: CI and code changes can be agent-owned, while physical APK testing on a specific device can remain user-owned.

## Related Concepts

- [[concepts/local-gradle-validation-ban-ci-only]] - Ozero validation ownership depends on approved gates and CI, not ad hoc local runs.
- [[concepts/release-runtime-scenario-checklist]] - Runtime release proof can require scenario/device checks beyond CI.
- [[concepts/ci-fresh-run-authority-contract]] - CI proof must be explicit and fresh, just as task scope must be explicit before work.
- [[concepts/memory-commit-with-work-only]] - Workflow state and memory changes should be bundled with the relevant work.

## Sources

- [[daily/2026-05-06.md]] - Session 12:43: WARP physical verification item `#12` was kept as a user reminder, not an agent-owned task.
- [[daily/2026-05-06.md]] - Session 13:16: user frustration was tied to starting WARP without a TODO; the session then created a 10-task list before continuing, and the lesson was recorded as "TODO before work."
