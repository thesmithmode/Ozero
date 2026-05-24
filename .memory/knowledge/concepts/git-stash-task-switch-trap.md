---
title: "Git Stash Task-Switch Trap"
aliases: [git-stash-unreliable, wip-commit-pattern, worktree-task-switch]
tags: [git, workflow, process, trap]
sources:
  - "daily/2026-05-07.md"
created: 2026-05-07
updated: 2026-05-07
---

# Git Stash Task-Switch Trap

Using `git stash` to shelve in-progress work before switching to an urgent task is unreliable. The stash can silently fail to reapply cleanly (`git stash apply` / `git stash pop`) if the base commit has diverged, producing merge conflicts or partial application that corrupts the working tree. In Ozero's 2026-05-07 incident, an auto-mode (#7) implementation stash was lost when CI-diversion commits changed the surrounding code — the stash applied with conflicts and the partially-applied state had to be discarded, requiring a full reimplementation from scratch.

## Key Points

- `git stash pop` / `apply` fails silently when the stashed diff no longer applies cleanly to HEAD — the stash remains but the tree is left in a conflicted state
- A rebased or amended stash on a diverged branch is particularly risky: the stash was made against an ancestor, the branch tip has since been rewritten
- Preferred alternative for planned task switches: create a WIP commit (`git commit -m "wip: ..."`) — a commit survives any number of branch operations and can be amended when returning
- For unplanned diversions (urgent bug on a different branch): use `git worktree add` to work on the urgent fix in a separate directory while the feature branch remains untouched
- If stash is the only option, immediately record the stash ref (`git stash list`) and verify reapplication on a throwaway branch before discarding the base commit

## Details

### The Failure Mode

`git stash` captures the working tree diff and the index as an unreferenced commit stack. The stash itself is preserved correctly. The failure occurs at reapplication: `git stash pop` applies the stash diff as a patch against the current HEAD. If intervening commits have changed the same files, the patch may reject hunks, leaving the working tree partially modified and the index in a conflicted state. The stash entry is consumed (with `pop`) even on partial application failure, meaning the captured changes may be irrecoverable if the tree was modified after the partial apply.

The risk escalates when the diversion includes:
- Commits that touch the same files as the stash
- Refactors that rename or move files the stash references
- Merge commits that change the common ancestor

### Recommended Alternatives

**WIP commit (preferred for same-branch diversions):**
```bash
git add -p  # stage intentional changes
git commit -m "wip: auto-mode #7 — incomplete, DO NOT RELEASE"
# ... do urgent work, fix, push ...
git reset HEAD~1  # restore WIP state when returning
```

The commit is immutable. No matter how many commits land between the WIP and the return, `git reset HEAD~1` recovers the exact state. The `wip:` prefix convention makes it visible in `git log` and prevents accidental release inclusion.

**Worktree (preferred for different-branch diversions):**
```bash
git worktree add ../ozero-hotfix hotfix/ci-green
# work in ../ozero-hotfix without disturbing main checkout
git worktree remove ../ozero-hotfix
```

The main checkout retains its full working tree state unmodified. No stash, no conflict risk.

### Discovery Context

During 2026-05-07 Session 13:24, auto-mode (#7) implementation was interrupted to fix a WARP CI failure. The in-progress work was stashed. After the WARP fix landed (c173637 + CI green), the stash was applied against the updated dev branch. The AWG-related commits had touched `WarpIniBuilder` and `RealWarpSdkBridge` — the same files the auto-mode stash modified. The apply produced conflicts, and the partial state was irrecoverable. Auto-mode was rewritten from scratch.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - The CI-first workflow that necessitates frequent task diversions; stash risk compounds in environments where urgent CI fixes interrupt feature work
- [[concepts/release-process]] - WIP commits must be clearly marked to avoid accidental inclusion in a release tag build

## Sources

- [[daily/2026-05-07.md]] - Session 13:24: auto-mode stash lost during WARP CI diversion; git stash + apply unreliable when intervening commits touch same files; WIP commit or worktree recommended
