---
title: "gh run list Stale Race in CI Watcher"
aliases: [gh-run-list-race, ci-watcher-stale-run, github-actions-watcher-race]
tags: [ci, github-actions, bash, monitoring, workflow]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# gh run list Stale Race in CI Watcher

When monitoring GitHub Actions CI using `gh run list --limit 1`, a watcher loop can return a stale `completed` result from a previous run if the new run has not yet appeared in the API. This causes the monitor to incorrectly report success or failure for the old run.

## Key Points

- `gh run list --limit 1` returns the most recent run known to the API, which may be a run from before the current push
- If a new workflow trigger was just issued, the new run ID may not appear for several seconds
- A watcher that matches on `status=completed` without verifying the run ID can consume the previous run's result
- Fix: capture the run ID immediately after push (`gh run list --limit 1 --json databaseId`) and monitor that specific ID
- Alternative: add a short initial delay after push before starting the watcher loop

## Details

### The Race Window

After `git push`, GitHub Actions schedules a new workflow run. The run ID is created asynchronously. A watcher that immediately polls `gh run list --limit 1` may see the previous run (already `completed`) before the new run appears. The loop matches `status=completed`, extracts `conclusion`, and exits — with the wrong run's result.

This is especially deceptive when the previous run had the same conclusion (e.g., `failure`) as the expected result, masking whether the fix actually helped.

### Robust Watcher Pattern

The CLAUDE.md watcher pattern avoids this by looping until `status=completed` using the grep-only approach. However, if `--limit 1` is used without filtering by a known run ID, the race still applies. The correct approach:

```bash
# Capture new run ID after push (wait briefly for it to appear)
sleep 5
RUN_ID=$(gh run list --branch BRANCH --limit 1 --workflow ci.yml --json databaseId -q '.[0].databaseId')
# Then monitor specific run
while true; do
  s=$(gh run view "$RUN_ID" --json status,conclusion 2>/dev/null)
  st=$(echo "$s" | grep -oE '"status":"[^"]*"' | cut -d'"' -f4)
  if [ "$st" = "completed" ]; then
    conc=$(echo "$s" | grep -oE '"conclusion":"[^"]*"' | cut -d'"' -f4)
    echo "CI_DONE($conc)"
    break
  fi
  sleep 45
done
```

### GitHub Actions Global Incidents

On 2026-05-26, a GitHub Actions global incident (10:57 UTC) caused CI triggers to not fire. Code was correct; the incident resolved and CI triggered automatically. Distinguish infrastructure incidents from watcher race issues before debugging.

## Related Concepts

- [[concepts/ci-workflow-discipline]] — CI monitoring discipline and --continue flag usage
- [[concepts/ci-gradle-log-reading]] — Reading actual CI failures once a run completes

## Sources

- [[daily/2026-05-26.md]] — Session 16:52: `gh run list --limit 1` may return old completed run if new run not yet visible; fix via explicit run ID; also: GitHub Actions global incident on 2026-05-26 causing CI to not trigger
