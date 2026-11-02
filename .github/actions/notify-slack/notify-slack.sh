#!/usr/bin/env bash
# Posts, updates or replies to a Slack message through the Web API.
#
# Env: SLACK_TOKEN CHANNEL STATUS TITLE BODY FIELDS LINKS TS THREAD_TS MENTION USERNAME
#      ICON_EMOJI RUN_URL. SLACK_API_URL overrides https://slack.com/api.
# USERNAME and ICON_EMOJI fall back to SLACK_USERNAME and SLACK_ICON_EMOJI (set them in a workflow
# `env:` block), then to MAGE CI and :crystal_ball:.
# Writes ts and channel to $GITHUB_OUTPUT.
# Always exits 0: a Slack problem is reported as a ::warning:: and never fails the caller.

set -uo pipefail

API_URL=${SLACK_API_URL:-https://slack.com/api}
CHANNEL=${CHANNEL:-C08HFEC8W9W}
STATUS=${STATUS:-info}
USERNAME=${USERNAME:-${SLACK_USERNAME:-MAGE CI}}
ICON_EMOJI=${ICON_EMOJI:-${SLACK_ICON_EMOJI:-:crystal_ball:}}
MAX_ATTEMPTS=3
MAX_WAIT=30

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

emit_outputs() {
  [ -n "${GITHUB_OUTPUT:-}" ] || return 0
  {
    echo "ts=$1"
    echo "channel=$2"
  } >> "$GITHUB_OUTPUT"
}

if [ -z "${SLACK_TOKEN:-}" ]; then
  echo "::notice::Slack token is empty; skipping Slack notification."
  emit_outputs "${TS:-}" "$CHANNEL"
  exit 0
fi

case "$STATUS" in
  pending) emoji="⏳" ;;
  success) emoji="✅" ;;
  failure) emoji="❌" ;;
  stopped) emoji="⏹️" ;;
  warning) emoji="⚠️" ;;
  info) emoji="ℹ️" ;;
  *)
    echo "::warning::Unknown Slack status '$STATUS'; using info."
    emoji="ℹ️"
    ;;
esac

if [ -n "${TS:-}" ]; then
  method=chat.update
else
  method=chat.postMessage
fi

if ! jq -n \
  --arg channel "$CHANNEL" \
  --arg emoji "$emoji" \
  --arg title "${TITLE:-}" \
  --arg body "${BODY:-}" \
  --arg fields "${FIELDS:-}" \
  --arg links "${LINKS:-}" \
  --arg run_url "${RUN_URL:-}" \
  --arg ts "${TS:-}" \
  --arg thread_ts "${THREAD_TS:-}" \
  --arg mention "${MENTION:-}" \
  --arg username "$USERNAME" \
  --arg icon_emoji "$ICON_EMOJI" \
  '
  def esc: gsub("&"; "&amp;") | gsub("<"; "&lt;") | gsub(">"; "&gt;");
  def trim: sub("^\\s+"; "") | sub("\\s+$"; "");
  def lines: split("\n") | map(gsub("\r"; "")) | map(select(test("\\S")));

  ($mention | trim) as $m
  | (if $m == "" then "" else $m + " " end) as $prefix
  | ($title | esc) as $t
  | ($fields | lines | map(
      if test(":")
      then capture("^(?<k>[^:]*):(?<v>.*)$") | {type: "mrkdwn", text: ("*" + (.k | trim | esc) + ":*\n" + (.v | trim | esc) | .[:2000])}
      else {type: "mrkdwn", text: (esc | .[:2000])}
      end
    ) | .[:10]) as $f
  | (($links | lines | map(select(contains("|")) | capture("^(?<l>[^|]*)\\|(?<u>.*)$") | {l: (.l | trim), u: (.u | trim)})
      | map(select(.u != "") | "<" + (.u | esc | gsub("\\|"; "%7C")) + "|" + (.l | esc) + ">"))
     + (if $run_url == "" then [] else ["<" + ($run_url | esc) + "|Run>"] end)) as $l
  | {
      channel: $channel,
      text: ($prefix + $emoji + " " + $t),
      blocks: (
        [{type: "section", text: {type: "mrkdwn", text: ($prefix + $emoji + " *" + $t + "*")}}]
        + (if ($body | test("\\S")) then [{type: "section", text: {type: "mrkdwn", text: ($body | .[:3000])}}] else [] end)
        + (if ($f | length) > 0 then [{type: "section", fields: $f}] else [] end)
        + (if ($l | length) > 0 then [{type: "context", elements: [{type: "mrkdwn", text: ($l | join("  ·  "))}]}] else [] end)
      ),
      unfurl_links: false,
      unfurl_media: false
    }
  + (if $ts != "" then {ts: $ts}
     else {username: $username, icon_emoji: $icon_emoji}
          + (if $thread_ts != "" then {thread_ts: $thread_ts} else {} end)
     end)
  ' > "$work/payload.json"; then
  echo "::warning::Could not build the Slack payload; no notification sent."
  emit_outputs "${TS:-}" "$CHANNEL"
  exit 0
fi

attempt=1
while :; do
  : > "$work/headers"
  : > "$work/response"
  code=$(printf 'header = "Authorization: Bearer %s"\n' "$SLACK_TOKEN" | curl -sS -K - \
    --max-time 30 \
    -X POST \
    -H 'Content-Type: application/json; charset=utf-8' \
    --data-binary @"$work/payload.json" \
    -D "$work/headers" \
    -o "$work/response" \
    -w '%{http_code}' \
    "$API_URL/$method" 2> "$work/curl_err")
  curl_status=$?

  if [ "$curl_status" -ne 0 ]; then
    echo "::warning::Slack request failed: $(tr '\n' ' ' < "$work/curl_err")"
    break
  fi

  if { [ "$code" = "429" ] || [[ "$code" == 5* ]]; } && [ "$attempt" -lt "$MAX_ATTEMPTS" ]; then
    wait_for=$(grep -i '^retry-after:' "$work/headers" | tail -1 | tr -dc '0-9')
    wait_for=${wait_for:-$((attempt * 2))}
    [ "$wait_for" -gt "$MAX_WAIT" ] && wait_for=$MAX_WAIT
    echo "Slack returned HTTP $code; retrying in ${wait_for}s (attempt $attempt of $MAX_ATTEMPTS)."
    sleep "$wait_for"
    attempt=$((attempt + 1))
    continue
  fi

  if [ "$code" != "200" ]; then
    echo "::warning::Slack returned HTTP $code."
    break
  fi

  if [ "$(jq -r '.ok // false' "$work/response" 2> /dev/null)" = "true" ]; then
    emit_outputs "$(jq -r '.ts // empty' "$work/response")" "$(jq -r --arg c "$CHANNEL" '.channel // $c' "$work/response")"
    exit 0
  fi

  echo "::warning::Slack API error: $(jq -r '.error // "unparseable response"' "$work/response" 2> /dev/null || echo "unparseable response")"
  break
done

emit_outputs "${TS:-}" "$CHANNEL"
exit 0
