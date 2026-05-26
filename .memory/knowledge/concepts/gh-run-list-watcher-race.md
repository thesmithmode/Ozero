---
title: "gh run list --limit 1 Watcher Race"
aliases: [ci-watcher-race, gh-run-list-stale]
tags: [ci, github-actions, gh-cli, monitoring, gotcha]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# gh run list --limit 1 Watcher Race

`gh run list --limit 1` can return a previously-completed run when a new run has been triggered but has not yet appeared in the GitHub Actions API. This causes a CI watcher loop to exit prematurely with a stale `completed` status, incorrectly signaling CI as done.

## Key Points

- `--limit 1` returns the most recent run known to the API — if the new run hasn't propagated yet, the previous completed run is returned
- The watcher loop may read `status=completed` from the old run and exit before the new run has even started
- Fix: capture the run ID immediately after triggering and monitor that specific run ID instead of using `--limit 1`
- Alternative: compare run `createdAt` against the trigger time to filter out stale entries
- This race is more likely when re-running CI quickly (e.g., push → fix → push within seconds)

## Details

### Reproduction Pattern

```bash
# Trigger new CI run
git push

# Watcher starts immediately — new run not yet in API
while true; do
  s=$(gh run list --branch dev-fix --limit 1 --workflow ci.yml --json status,conclusion)
  st=$(echo "$s" | grep -oE '"status":"[^"]*"' | cut -d'"' -f4)
  if [ "$st" = "completed" ]; then
    # ← This fires on the PREVIOUS completed run, not the new one
    conc=$(echo "$s" | grep -oE '"conclusion":"[^"]*"' | cut -d'"' -f4)
    echo "CI_DONE($conc)"
    break
  fi
  sleep 45
done
```

### Correct Approach

Capture the run ID at trigger time:
```bash
# Get the run ID immediately after push, retry until new run appears
NEW_RUN_ID=$(gh run list --branch dev-fix --limit 1 --workflow ci.yml --json databaseId,createdAt \
  | ... # filter by createdAt > trigger_time)

# Monitor specific run ID
gh run watch $NEW_RUN_ID
```

Or use `gh run watch` directly after obtaining the ID, which handles polling internally.

### Context

Discovered during CI monitoring for `dev-fix` branch run 26451747627. The watcher loop read the previous completed run and reported CI as done before the actual new run (triggered after a ktlint fix push) had started. This is consistent with the `CLAUDE.md` CI watcher pattern that uses `--limit 1` — a follow-up to that pattern is to add run-ID anchoring for rapid re-trigger scenarios.

## Related Concepts

- [[concepts/ci-workflow-discipline]] - CI watcher patterns and `gh run list` usage
- [[concepts/ci-gradle-log-reading]] - How to read actual CI failures once a run ID is known
- [[concepts/detekt-ratchet-desync-after-refactor]] - CI failures that prompt rapid re-push cycles where this race is most likely

## Sources

- [[daily/2026-05-26.md]] — Session 16:52: `gh run list --limit 1` watcher race — new run not yet visible → stale completed run returned → false exit; fix via explicit run ID anchoring
