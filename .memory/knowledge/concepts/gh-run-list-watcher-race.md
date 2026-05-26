---
title: "gh run list --limit 1 Watcher Race Condition"
aliases: [gh-run-stale, ci-watcher-race, run-list-anchor]
tags: [ci, github-actions, bash, monitoring, race-condition]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# gh run list --limit 1 Watcher Race Condition

`gh run list --limit 1` returns the most recently completed run known to the GitHub API at the moment of the call. When a new run has been triggered but not yet indexed by the API, `--limit 1` returns the previous (stale) completed run. A watcher that polls `status=completed` can exit immediately with the wrong run's conclusion, misreporting CI as passed or failed when it actually hasn't finished yet.

## Key Points

- `gh run list --limit 1` fetches from GitHub API; new runs have a delay (seconds to minutes) before appearing
- If the previous run was `completed/success` and the new run hasn't appeared yet, the watcher sees `completed` immediately and exits incorrectly
- The fix: anchor the watcher to a specific run ID obtained at trigger time — do not rely on `--limit 1` to return the current run
- Pattern: capture `runId` from `gh run list` immediately after `git push`, then poll `gh run view $runId --json status,conclusion`
- The CLAUDE.md CI watcher pattern uses `--limit 1` with `--branch BRANCH` as a heuristic; for critical waits, prefer run ID anchoring

## Details

### The Race Window

The race window exists between:
1. `git push` triggers the GitHub Actions workflow
2. The new run appears in `gh run list` output

During this window (which can be 10–60+ seconds depending on queue state and API propagation), `gh run list --limit 1 --branch BRANCH` returns the **previous** run. If that previous run was `completed`, the poll loop sees `status=completed` and exits with the stale conclusion.

```bash
# Vulnerable pattern (CLAUDE.md default):
while true; do
  s=$(gh run list --branch dev --limit 1 --workflow ci.yml --json status,conclusion 2>/dev/null)
  st=$(echo "$s" | grep -oE '"status":"[^"]*"' | cut -d'"' -f4)
  if [ "$st" = "completed" ]; then
    # BUG: may be stale run if new run not yet in API
    echo "CI_DONE($(echo "$s" | grep -oE '"conclusion":"[^"]*"' | cut -d'"' -f4))"
    break
  fi
  sleep 45
done
```

### Run ID Anchoring Fix

```bash
# Capture run ID right after push
RUN_ID=$(gh run list --branch dev --limit 1 --workflow ci.yml --json databaseId \
  | grep -oE '"databaseId":[0-9]+' | head -1 | cut -d: -f2)

# Poll by ID — no stale run possible
while true; do
  s=$(gh run view $RUN_ID --json status,conclusion 2>/dev/null)
  st=$(echo "$s" | grep -oE '"status":"[^"]*"' | cut -d'"' -f4)
  if [ "$st" = "completed" ]; then
    echo "CI_DONE($(echo "$s" | grep -oE '"conclusion":"[^"]*"' | cut -d'"' -f4))"
    break
  fi
  sleep 45
done
```

### When This Matters

This race is benign in casual monitoring (the next poll will see the correct run). It becomes a problem when the watcher exit is used as a gate: if the watcher exits with `CI_DONE(success)` from a stale run, downstream actions (e.g., tagging a release, marking a task complete) proceed incorrectly while the actual CI run is still pending.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI monitoring patterns and discipline
- [[concepts/detekt-toomany-functions-semantics]] - A CI failure type that triggers watcher monitoring
- [[concepts/release-process]] - Release gating depends on accurate CI status reads

## Sources

- [[daily/2026-05-26.md]] - Session 16:52: discovered `gh run list --limit 1` returns stale completed run when new run not yet in API; fix = anchor to explicit run ID in monitoring; identified during CI iteration after ktlint fix push
