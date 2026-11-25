#!/usr/bin/env bash
set -euo pipefail

cache_dir="${1:?missing cache directory}"
repository="${GITHUB_REPOSITORY:?missing GITHUB_REPOSITORY}"
read -r tag expected < .github/checksums/urnetwork-sdk.lock
if [[ ! "$tag" =~ ^urnetwork-sdk-[a-f0-9]{8}$ || ! "$expected" =~ ^[a-f0-9]{64}$ ]]; then
  echo "::error::Invalid URnetwork SDK lock" >&2
  exit 1
fi

asset="URnetworkSdk.aar"
original="$cache_dir/$asset"
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

verify() {
  printf '%s  %s\n' "$expected" "$1" | sha256sum --check --status
}

if [[ ! -s "$original" ]] || ! verify "$original"; then
  gh release download "$tag" --repo "$repository" --pattern "$asset" --dir "$work_dir"
  if ! verify "$work_dir/$asset"; then
    echo "::error::URnetwork SDK SHA-256 mismatch for $tag" >&2
    exit 1
  fi
  mkdir -p "$cache_dir"
  cp "$work_dir/$asset" "$original"
fi

mkdir "$work_dir/unpacked"
unzip -q "$original" -d "$work_dir/unpacked"
classes="$work_dir/unpacked/classes.jar"
zipinfo -1 "$classes" > "$work_dir/classes.txt"
if grep -q '^go/.*\.class$' "$work_dir/classes.txt"; then
  zip -qd "$classes" 'go/*.class'
fi

mkdir -p engine-urnetwork/libs
prepared="$work_dir/prepared.aar"
(cd "$work_dir/unpacked" && zip -qr9 "$prepared" .)
mv "$prepared" "engine-urnetwork/libs/$asset"
echo "Verified and prepared URnetwork SDK $tag"
