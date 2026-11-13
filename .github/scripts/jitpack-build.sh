#!/usr/bin/env bash
# Polls JitPack until it builds artifacts for a given version (tag or commit SHA).
# Deletes and retriggers failed builds that look transient (rate limits, network
# errors) so GitHub re-runs actually rebuild instead of re-reading JitPack's
# cached failure. Requires JITPACK_TOKEN for delete/retrigger; without it, falls
# back to poll-only behavior.
#
# Usage: jitpack-build.sh <version>
# Exit:  0 = built and verified, 1 = failed, 2 = timed out / version unresolved,
#        3 = throttled (JitPack answered 429 and the build state could not be confirmed)
# Env:   JITPACK_TOKEN (optional) - JitPack API token with push access
#        FORCE_REBUILD (optional) - "true" deletes any existing build before polling

set -u

if [ $# -ne 1 ]; then
  echo "Usage: jitpack-build.sh <version>" >&2
  exit 2
fi

VERSION="$1"
GROUP_PATH="com.github.klaviyo"
ARTIFACT="klaviyo-android-sdk"
LOG_URL="https://jitpack.io/com/github/klaviyo/klaviyo-android-sdk/${VERSION}/build.log"
ARTIFACT_URL="https://jitpack.io/com/github/klaviyo/klaviyo-android-sdk/${VERSION}/"
API_URL="https://jitpack.io/api/builds/${GROUP_PATH}/${ARTIFACT}/${VERSION}"

MAX_ATTEMPTS=3
MAX_RUNTIME=900 # per attempt, seconds
INTERVAL=30
BACKOFFS=(60 180)
THROTTLE_BACKOFF_START=60
THROTTLE_BACKOFF_MAX=240
THROTTLE_MAX_WAIT=1800 # total seconds spent waiting out 429s per attempt before giving up polling

EXPECTED_ARTIFACTS=("analytics" "core" "forms" "forms-core" "location" "location-core" "inbox" "inbox-core" "push-fcm")

TRANSIENT_PATTERN='Too Many Requests|status code 50[234]|50[234] (Bad Gateway|Service Unavailable|Gateway Time-?out)|Read timed out|Connect timed out|Connection reset'

LOG_FILE=$(mktemp)
BODY_FILE=$(mktemp)
HEADERS_FILE=$(mktemp)
trap 'rm -f "$LOG_FILE" "$BODY_FILE" "$HEADERS_FILE"' EXIT

LOG_HTTP_CODE=""
LAST_LOG_THROTTLED=false

if [ -z "${JITPACK_TOKEN:-}" ]; then
  echo "::warning::JITPACK_TOKEN is not set; failed builds will not be deleted or retriggered. Retries are disabled."
  RETRY_ENABLED=false
else
  RETRY_ENABLED=true
fi

# GET the JitPack builds API; prints the status field (e.g. "ok", "Error"), empty on request failure.
jitpack_status() {
  local response
  response=$(curl --silent -m 30 "$API_URL") || return 1
  echo "$response" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4
}

# DELETE the cached build so the next request rebuilds. Returns 0 on success.
delete_build() {
  local http_code
  http_code=$(curl --silent -o /dev/null -w "%{http_code}" -m 30 -X DELETE -u "$JITPACK_TOKEN:" "$API_URL")
  if [ "$http_code" = "200" ] || [ "$http_code" = "204" ]; then
    echo "Deleted cached JitPack build (HTTP $http_code)"
    return 0
  fi
  echo "::warning::Failed to delete JitPack build (HTTP $http_code)"
  return 1
}

# Request the build log and artifact directory so JitPack starts (or restarts) a build.
trigger_build() {
  curl --silent -o /dev/null -m 30 "$LOG_URL" || true
  curl --silent -o /dev/null -m 30 "$ARTIFACT_URL" || true
}

# Fetch the build log once. Sets LOG_HTTP_CODE and LAST_LOG_THROTTLED; LOG_FILE is only
# replaced by non-429 responses, so a throttled poll keeps the last real log output.
fetch_log() {
  LOG_HTTP_CODE=$(curl --silent -m 30 -D "$HEADERS_FILE" -o "$BODY_FILE" -w '%{http_code}' "$LOG_URL") || LOG_HTTP_CODE=000
  if [ "$LOG_HTTP_CODE" = "429" ]; then
    LAST_LOG_THROTTLED=true
  else
    LAST_LOG_THROTTLED=false
    cat "$BODY_FILE" >"$LOG_FILE"
  fi
}

# Print the Retry-After header from the last fetch when it is a whole number of seconds.
retry_after_seconds() {
  local value
  value=$(grep -i '^retry-after:' "$HEADERS_FILE" | tail -n 1 | tr -d '\r' | sed 's/^[^:]*:[[:space:]]*//')
  case "$value" in
    '' | *[!0-9]*) return 1 ;;
  esac
  echo "$value"
}

# Poll the build log for up to MAX_RUNTIME seconds, storing the latest output in LOG_FILE.
# Time spent waiting out HTTP 429 responses does not count toward MAX_RUNTIME, but polling
# stops once those waits total THROTTLE_MAX_WAIT.
# Returns: 0 = BUILD SUCCESSFUL, 1 = BUILD FAILED, 2 = timeout
poll_build() {
  local start=$SECONDS
  local throttled_wait=0
  local backoff=$THROTTLE_BACKOFF_START
  local wait_seconds

  LAST_LOG_THROTTLED=false
  echo "Polling $LOG_URL"
  while [ $((SECONDS - start - throttled_wait)) -lt "$MAX_RUNTIME" ] && [ "$throttled_wait" -lt "$THROTTLE_MAX_WAIT" ]; do
    fetch_log

    if [ "$LAST_LOG_THROTTLED" = true ]; then
      if wait_seconds=$(retry_after_seconds); then
        echo "  [$((SECONDS - start))s] JitPack returned HTTP 429 (Retry-After: ${wait_seconds}s); waiting."
      else
        wait_seconds=$backoff
        echo "  [$((SECONDS - start))s] JitPack returned HTTP 429; backing off ${wait_seconds}s."
        backoff=$((backoff * 2))
        if [ "$backoff" -gt "$THROTTLE_BACKOFF_MAX" ]; then
          backoff=$THROTTLE_BACKOFF_MAX
        fi
      fi
      if [ "$wait_seconds" -gt $((THROTTLE_MAX_WAIT - throttled_wait)) ]; then
        wait_seconds=$((THROTTLE_MAX_WAIT - throttled_wait))
      fi
      sleep "$wait_seconds"
      throttled_wait=$((throttled_wait + wait_seconds))
      continue
    fi
    backoff=$THROTTLE_BACKOFF_START

    if grep -q "BUILD SUCCESSFUL" "$LOG_FILE"; then
      return 0
    elif grep -q "BUILD FAILED" "$LOG_FILE"; then
      return 1
    fi

    echo "  [$((SECONDS - start - throttled_wait))s] build in progress..."
    sleep "$INTERVAL"
  done

  return 2
}

# Report that JitPack throttled us and the build state is unknown; the workflow reads this output.
report_throttled() {
  echo "::warning::JitPack throttled build log requests (HTTP 429) and the build state could not be confirmed."
  if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "throttled=true" >>"$GITHUB_OUTPUT"
  fi
  exit 3
}

# Print the Gradle failure report ("What went wrong:" onward), or the last 100 lines if there is none.
failure_section() {
  if grep -q "What went wrong:" "$LOG_FILE"; then
    sed -n '/What went wrong:/,$p' "$LOG_FILE"
  else
    tail -n 100 "$LOG_FILE"
  fi
}

# Check the successful build output for every expected artifact.
verify_artifacts() {
  local missing=()
  local artifact

  for artifact in "${EXPECTED_ARTIFACTS[@]}"; do
    if ! grep -q "Found artifact: com.klaviyo:${artifact}:" "$LOG_FILE"; then
      missing+=("$artifact")
    fi
  done

  if [ "${#missing[@]}" -ne 0 ]; then
    echo "ERROR: Missing artifacts: ${missing[*]}"
    return 1
  fi
  echo "Verified: all expected artifacts published (${EXPECTED_ARTIFACTS[*]})"
  return 0
}

# --- Pre-flight: clear a stale failed build so this run really rebuilds ---
status=$(jitpack_status || true)
echo "Current JitPack build status: ${status:-unknown}"
case "${status,,}" in
  tagnotfound|giterror)
    echo "JitPack cannot resolve version '$VERSION' yet (status: $status); requesting it and re-checking in ${INTERVAL}s."
    trigger_build
    sleep "$INTERVAL"
    status=$(jitpack_status || true)
    echo "JitPack build status after re-check: ${status:-unknown}"
    case "${status,,}" in
      tagnotfound|giterror)
        echo "JitPack cannot resolve version '$VERSION' (status: $status)."
        exit 2
        ;;
    esac
    ;;
  error)
    if [ "$RETRY_ENABLED" = true ]; then
      echo "JitPack has a cached failed build; deleting and retriggering."
      delete_build || true
    else
      echo "::warning::JitPack has a cached failed build, but retries are disabled without JITPACK_TOKEN. Polling will re-read the stale failure."
    fi
    ;;
  ok)
    if [ "${FORCE_REBUILD:-false}" = true ]; then
      if [ "$RETRY_ENABLED" = true ]; then
        echo "Force rebuild requested; deleting the existing JitPack build."
        delete_build || true
      else
        echo "::warning::Force rebuild requested, but JITPACK_TOKEN is not set; reusing the existing build."
      fi
    fi
    ;;
esac

attempt=1
while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
  echo "::group::Attempt $attempt of $MAX_ATTEMPTS"

  trigger_build
  poll_build
  result=$?

  if [ "$result" -eq 0 ]; then
    echo "Build Complete!"
    cat "$LOG_FILE"
    echo "::endgroup::"
    verify_artifacts
    exit $?
  fi

  if [ "$result" -eq 2 ]; then
    echo "Build timed out after ${MAX_RUNTIME}s. Last status:"
    cat "$LOG_FILE"
    echo "::endgroup::"

    status=$(jitpack_status || true)
    echo "JitPack build status after timeout: ${status:-unknown}"
    if [ "${status,,}" = ok ]; then
      fetch_log
      if verify_artifacts; then
        exit 0
      fi
      if [ "$LAST_LOG_THROTTLED" = true ]; then
        report_throttled
      fi
      exit 1
    fi
    if [ "$LAST_LOG_THROTTLED" = true ]; then
      report_throttled
    fi
    exit 2
  fi

  # result == 1: BUILD FAILED
  if failure_section | grep -qE "$TRANSIENT_PATTERN"; then
    transient=true
  else
    transient=false
  fi

  if [ "$transient" = false ]; then
    echo "Build failed with a non-transient error; not retrying."
    cat "$LOG_FILE"
    echo "::endgroup::"
    exit 1
  fi

  if [ "$RETRY_ENABLED" = false ]; then
    echo "Build failed with a transient-looking error, but retries are disabled without JITPACK_TOKEN."
    cat "$LOG_FILE"
    echo "::endgroup::"
    exit 1
  fi

  if [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
    echo "Build failed with a transient-looking error after $MAX_ATTEMPTS attempts."
    cat "$LOG_FILE"
    echo "::endgroup::"
    exit 1
  fi

  backoff=${BACKOFFS[$((attempt - 1))]}
  echo "Transient failure detected (attempt $attempt). Deleting build and retrying in ${backoff}s."
  if ! delete_build; then
    echo "Could not delete the failed build, so a retry would re-read the same failure; giving up."
    cat "$LOG_FILE"
    echo "::endgroup::"
    exit 1
  fi
  echo "::endgroup::"
  sleep "$backoff"
  attempt=$((attempt + 1))
done

exit 1
