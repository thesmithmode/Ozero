---
title: "Git Active Branch Check Before Starting Work"
aliases: [git-branch-check, wrong-branch-trap, branch-verification]
tags: [git, workflow, discipline, android]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# Git Active Branch Check Before Starting Work

Always verify the active git branch as the very first step before starting any code changes. Working in the wrong branch (e.g., a stale `win11` branch instead of `dev`) contaminates the wrong branch with commits and forces a disruptive recovery. This failure mode is particularly insidious with AI agents that proceed autonomously — they may complete substantial work before the branch error is noticed.

## Key Points

- Run `git branch` or `git status` as step 0 before any file edits or tool calls
- In Ozero: `dev` is the default working branch; `main` is release-only (never touch without explicit command)
- Stale feature branches (e.g., `win11`) may still exist after merge — do not work in them
- CI watcher scripts should include `--branch dev` to avoid monitoring the wrong branch's runs
- Branch error discovered mid-session requires: stash/revert changes, checkout correct branch, re-apply

## Details

### The Incident

In session 19:44, an agent was assigned to investigate Ozero v0.2.8/v0.2.9 bugs. The agent began reading logs and making code changes, but was working in the `win11` branch — a stale branch from an earlier Windows-specific investigation — instead of `dev`. The user caught the error mid-session and stopped work. All changes made in the wrong branch were lost or required manual cherry-picking.

The `win11` branch existed as a stale remote because it was never deleted after its work was merged. AI agents do not automatically verify branch context before beginning work, so they proceed in whatever branch happens to be checked out.

### Prevention Protocol

Before any code modification in a session:

```bash
git branch          # see current branch
git status          # see dirty state
git log --oneline -3  # verify recent commits match expected context
```

If the wrong branch is active:
```bash
git stash           # save any local changes
git checkout dev    # switch to correct branch
git stash pop       # re-apply if applicable
```

### CI Monitoring Discipline

When monitoring CI with `gh run list --branch BRANCH`, always specify the correct branch explicitly. A stale branch may have its own recent runs that confuse the watcher. In Ozero, the canonical monitor command uses `--branch dev`.

### Relationship to CLAUDE.md

The Ozero CLAUDE.md states: "main не трогать без явной команды." This implies `dev` is the default. However, explicit branch verification is not enforced by any hook — it is a process discipline that each session/agent must apply manually as step 0.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI branch discipline and monitoring patterns
- [[concepts/git-stash-task-switch-trap]] - git stash reliability issues for context switching
- [[concepts/release-process]] - Ozero branch topology: dev → tag → release.yml

## Sources

- [[daily/2026-05-26.md]] - Session 19:44: agent started work in stale `win11` branch instead of `dev`; user caught and stopped; established rule: `git branch` as absolute first step before any work; context = Ozero v0.2.8/v0.2.9 bug investigation session
