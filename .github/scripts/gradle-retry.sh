#!/usr/bin/env bash
# Retry Gradle only when dependency requests fail with transient repository errors.
set -uo pipefail

if [[ $# -eq 0 ]]; then
  echo "Usage: gradle-retry.sh <command> [args...]" >&2
  exit 2
fi

max_attempts=3
attempt=1
while true; do
  log_file=$(mktemp)
  "$@" 2>&1 | tee "$log_file"
  command_status=${PIPESTATUS[0]}

  if [[ $command_status -eq 0 ]]; then
    rm -f "$log_file"
    exit 0
  fi

  retryable_http_error=false
  if grep -Eiq "Could not (GET|HEAD) 'https?://[^']+'\. Received status code (429|5[0-9]{2})" "$log_file"; then
    retryable_http_error=true
  elif grep -Eiq "Could not (GET|HEAD) 'https?://[^']+'" "$log_file" &&
       grep -Eiq 'Read timed out|Connect timed out|UnknownHostException|Temporary failure in name resolution|Connection reset by peer|remote host terminated the handshake' "$log_file"; then
    retryable_http_error=true
  fi

  if [[ "$retryable_http_error" != true || $attempt -ge $max_attempts ]]; then
    rm -f "$log_file"
    exit "$command_status"
  fi

  rm -f "$log_file"
  delay=$((15 * (1 << (attempt - 1)) + RANDOM % 15))
  echo "::warning::Transient Gradle repository failure; retrying attempt $((attempt + 1))/$max_attempts in ${delay}s."
  sleep "$delay"
  attempt=$((attempt + 1))
done
