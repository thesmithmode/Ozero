---
title: "Git Contributor Rewrite: Removing AI Authorship"
aliases: [git-rebase-amend-author, remove-claude-author, git-contributor-cache]
tags: [git, process, workflow, github]
sources:
  - "daily/2026-05-24.md"
created: 2026-05-24
updated: 2026-05-24
---

# Git Contributor Rewrite: Removing AI Authorship

When a commit lands in the repository with an AI tool as author (e.g., `Author: Claude`), it must be rewritten using `git rebase -i` + `git commit --amend --author` on the specific commit, followed by force-push. The GitHub contributors page caches contributor data and may still show the AI author after force-push — this is a display cache, not a data error; the API reflects the true state immediately.

## Key Points

- `git rebase -i` + `git commit --amend --author="name <email>"` is the correct tool for single-commit author rewrite on Windows (no `git filter-repo` needed)
- Force-push `dev` after the amend to rewrite branch history; only safe on non-protected branches
- GitHub contributors UI caches data — may show stale authors for 10-15 minutes after force-push
- Verify clean state via API: `gh api repos/OWNER/REPO/contributors` — returns true data without cache
- Ozero global rule: AI signatures banned everywhere; `Co-Authored-By: Claude` and `noreply@anthropic.com` forbidden

## Details

### Incident (2026-05-24)

During the v0.3.0 release preparation, commit `6df7ca13` was found with `Author: Claude` (AI tool had generated the commit). Since Ozero's public repository would permanently index this attribution, the commit was rewritten:

```bash
git rebase -i <parent-sha>
# Mark the commit as 'edit'
git commit --amend --author="thesmithmode <117716736+thesmithmode@users.noreply.github.com>"
git rebase --continue
git push --force-with-lease origin dev
```

### GitHub Contributor Cache

After the force-push, the GitHub repository contributors page (`/graphs/contributors`) still showed the AI author. This is a display cache — GitHub aggregates contributor statistics asynchronously. The real data is accessible immediately via the API:

```bash
gh api repos/OWNER/REPO/contributors
```

The cache self-clears within 10-15 minutes. No action needed beyond verifying via API.

### Prevention

The root cause was an automated agent generating a commit without the correct author. Prevention measures:
- `CLAUDE.md` rule: AI signatures forbidden; `Co-Authored-By: Claude`, `noreply@anthropic.com`, `Generated with Claude Code` are banned in all commits
- Pre-push check: `git show HEAD` to verify author before every push
- Subagents are forbidden from running `git commit` (see feedback: agents do not commit)

## Related Concepts

- [[concepts/ci-workflow-discipline]] - Force-push to dev is allowed; force-push to main is never permitted
- [[concepts/release-process]] - v0.3.0 squash merge dev→main happened in the same session

## Sources

- [[daily/2026-05-24.md]] — Session 15:46: commit `6df7ca13` had `Author: Claude`; rewritten via `git rebase -i` + `git commit --amend --author`; force-push to dev; GitHub contributor page cached stale data, API was clean; cache cleared within 10-15 min
