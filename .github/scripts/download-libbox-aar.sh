#!/usr/bin/env bash
set -euo pipefail

destination="${1:?missing destination}"
version="${2:?missing libbox version}"
repository="${GITHUB_REPOSITORY:?missing GITHUB_REPOSITORY}"
directory="$(dirname "$destination")"
asset="$(basename "$destination")"

mkdir -p "$directory"
for attempt in 1 2 3 4 5; do
  rm -f "$destination"
  if gh release download "singbox-$version" -p "$asset" -D "$directory" --clobber -R "$repository" && [ -s "$destination" ]; then
    exit 0
  fi
  if [ "$attempt" -lt 5 ]; then
    sleep "$((attempt * 3))"
  fi
done

echo "::error::failed to download libbox.aar after 5 attempts" >&2
exit 1
