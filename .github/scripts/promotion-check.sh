#!/usr/bin/env bash
# Proves every commit in a PR was already reviewed in its own PR, so a promotion
# PR (feat -> rel, rel -> master, back-merges) can be admin merged with evidence.
#
# Commit set: base...head minus commits already on base by patch-id.
#   Non-merge commit: passes when its merged source PR has an APPROVED review from
#     a reviewer who is not the PR author, not a bot, and whose review
#     author_association is OWNER or MEMBER (an org member). Commits pushed after
#     the approval are counted. Also passes, labeled exempt, when that PR was
#     merged by evan-masseau without a qualifying approval, or when the commit has
#     no source PR and both its author and committer are github-actions[bot].
#   Merge commit: passes only when `git show --remerge-diff` is empty.
#
# Writes a table to $GITHUB_STEP_SUMMARY (stdout when unset). When every commit
# passes, creates or updates one PR comment; when a later run fails, an existing
# comment is rewritten to say so. Informational: exits non-zero only on error.
#
# Usage: promotion-check.sh <owner/repo> <pr-number> <base-ref> <head-ref>
# Env:   GH_TOKEN (required by gh)
#        DRY_RUN (optional) - "true" never writes a PR comment

set -euo pipefail

if [ $# -ne 4 ]; then
  echo "Usage: promotion-check.sh <owner/repo> <pr-number> <base-ref> <head-ref>" >&2
  exit 2
fi

REPO="$1"
PR_NUMBER="$2"
BASE="$3"
HEAD="$4"
DRY_RUN="${DRY_RUN:-false}"

MARKER="<!-- promotion-check -->"
COMMENT_BOT="github-actions[bot]"
EXEMPT_MERGER="evan-masseau"
COMMENT_LIMIT=60000
MAX_AFTER_SHAS=5
PASS_HEADER="✅ Every commit in this PR was already reviewed in its own PR. OK to admin merge."
NEW_LABEL="new in this PR (needs review)"
STALE_HEADER="⚠️ This PR no longer passes the promotion check. Not every commit was already reviewed in its own PR."

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
ROWS="$TMP/rows"
: >"$ROWS"

lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

short_list() {
  local shas="$1" out="" count=0 sha
  while IFS= read -r sha; do
    [ -z "$sha" ] && continue
    count=$((count + 1))
    if [ "$count" -le "$MAX_AFTER_SHAS" ]; then
      out="${out:+$out, }\`${sha:0:7}\`"
    fi
  done <<<"$shas"
  if [ "$count" -gt "$MAX_AFTER_SHAS" ]; then
    out="$out, …"
  fi
  printf '%s' "$out"
}

# Writes "$TMP/pr-<n>" as: status|approvers|after_count|after_shas_csv|after_known|merged_by
evaluate_pr() {
  local n="$1" file="$TMP/pr-$1"
  [ -f "$file" ] && return 0

  local pr author merged_by approvals approvers latest status
  local after="" after_count=0 after_known=1 pr_commits
  pr=$(gh api "repos/$REPO/pulls/$n")
  author=$(jq -r '.user.login' <<<"$pr")
  merged_by=$(jq -r '.merged_by.login // ""' <<<"$pr")

  approvals=$(gh api --paginate "repos/$REPO/pulls/$n/reviews?per_page=100" |
    jq -r --arg author "$author" '
      .[]
      | select(.state == "APPROVED" and .user != null and .user.type != "Bot")
      | select(.user.login | endswith("[bot]") | not)
      | select((.user.login | ascii_downcase) != ($author | ascii_downcase))
      | select(.author_association == "OWNER" or .author_association == "MEMBER")
      | [.user.login, .commit_id, .submitted_at] | join("|")')

  if [ -n "$approvals" ]; then
    status="approved"
    approvers=$(cut -d'|' -f1 <<<"$approvals" | sort -u | paste -sd, -)
    latest=$(sort -t'|' -k3 <<<"$approvals" | tail -n 1 | cut -d'|' -f2)
    pr_commits=$(gh api --paginate "repos/$REPO/pulls/$n/commits?per_page=100" --jq '.[].sha')
    if grep -qx "$latest" <<<"$pr_commits"; then
      after=$(awk -v c="$latest" 'f { print } $0 == c { f = 1 }' <<<"$pr_commits")
      if [ -n "$after" ]; then
        after_count=$(grep -c . <<<"$after")
      fi
    else
      after_known=0
    fi
  elif [ -n "$merged_by" ] && [ "$(lower "$merged_by")" = "$EXEMPT_MERGER" ]; then
    status="exempt"
    approvers=""
  else
    status="none"
    approvers=""
  fi

  printf '%s|%s|%s|%s|%s|%s\n' "$status" "$approvers" "$after_count" \
    "$(paste -sd, - <<<"$after")" "$after_known" "$merged_by" >"$file"
}

add_row() { printf '%s|%s|%s|%s|%s\n' "$1" "$2" "$3" "$4" "$5" >>"$ROWS"; }

check_commit() {
  local sha="$1"

  local parents
  parents=$(git rev-list --parents -n 1 "$sha" | wc -w | tr -d ' ')
  if [ "$((parents - 1))" -gt 1 ]; then
    if [ -n "$(git show --remerge-diff --format= "$sha")" ]; then
      add_row "$sha" 0 "" "merge commit introduces changes beyond its parents" ""
    else
      add_row "$sha" 1 "" "merge commit, nothing beyond its parents" ""
    fi
    return 0
  fi

  local pulls numbers in_current any_other
  pulls=$(gh api --paginate "repos/$REPO/commits/$sha/pulls?per_page=100" | jq -s 'add // []')
  numbers=$(jq -r --argjson current "$PR_NUMBER" \
    '.[] | select(.merged_at != null and .number != $current) | .number' <<<"$pulls" | sort -nu)
  in_current=$(jq --argjson current "$PR_NUMBER" '[.[] | select(.number == $current)] | length' <<<"$pulls")
  any_other=$(jq --argjson current "$PR_NUMBER" '[.[] | select(.number != $current)] | length' <<<"$pulls")

  if [ -z "$numbers" ]; then
    if [ "$(git log -1 --format='%an|%cn' "$sha")" = "$COMMENT_BOT|$COMMENT_BOT" ]; then
      add_row "$sha" 1 "" "exempt: automation: $COMMENT_BOT" ""
    elif [ "$in_current" -gt 0 ]; then
      add_row "$sha" 0 "" "$NEW_LABEL" ""
    elif [ "$any_other" -gt 0 ]; then
      add_row "$sha" 0 "" "no merged source PR" ""
    else
      add_row "$sha" 0 "" "no PR (direct push)" ""
    fi
    return 0
  fi

  local n chosen="" chosen_status="" status
  for n in $numbers; do
    evaluate_pr "$n"
    status=$(cut -d'|' -f1 "$TMP/pr-$n")
    if [ -z "$chosen" ] || { [ "$status" = "approved" ] && [ "$chosen_status" != "approved" ]; } ||
      { [ "$status" = "exempt" ] && [ "$chosen_status" = "none" ]; }; then
      chosen="$n"
      chosen_status="$status"
    fi
  done

  local approvers after_count after_csv after_known merged_by
  IFS='|' read -r _ approvers after_count after_csv after_known merged_by <"$TMP/pr-$chosen"

  case "$chosen_status" in
    approved)
      local detail="@${approvers//,/, @}" after_text
      if [ "$after_known" = "1" ]; then
        after_text="$after_count commits after approval"
        if [ "$after_count" -gt 0 ]; then
          after_text="$after_text ($(short_list "${after_csv//,/$'\n'}"))"
        fi
      else
        after_text="approved commit no longer in PR history"
      fi
      add_row "$sha" 1 "$chosen" "$detail" "$after_text"
      ;;
    exempt)
      add_row "$sha" 1 "$chosen" "exempt: admin merge by $EXEMPT_MERGER" ""
      ;;
    *)
      add_row "$sha" 0 "$chosen" "merged by @${merged_by:-unknown} without a qualifying approval" ""
      ;;
  esac
}

render_table() {
  local limit="${1:-0}" total shown=0 sha ok pr detail after mark pr_cell
  total=$(grep -c . "$ROWS")
  printf '| Commit | Source PR | Approver / reason | After approval |\n'
  printf '| --- | --- | --- | --- |\n'
  while IFS='|' read -r sha ok pr detail after; do
    if [ "$limit" -gt 0 ] && [ "$shown" -ge "$limit" ]; then
      break
    fi
    shown=$((shown + 1))
    mark="❌"
    [ "$ok" = "1" ] && mark="✅"
    pr_cell="none"
    [ -n "$pr" ] && pr_cell="[#$pr](https://github.com/$REPO/pull/$pr)"
    printf '| %s \x60%s\x60 | %s | %s | %s |\n' "$mark" "${sha:0:7}" "$pr_cell" "$detail" "$after"
  done <"$ROWS"
  if [ "$limit" -gt 0 ] && [ "$total" -gt "$limit" ]; then
    printf '| … | | %s more rows, see the job summary | |\n' "$((total - limit))"
  fi
}

comment_body() {
  local header="$1" limit=0 body total
  total=$(grep -c . "$ROWS")
  while true; do
    body="$MARKER"$'\n'"$header"$'\n\n'"$(render_table "$limit")"
    if [ "${#body}" -le "$COMMENT_LIMIT" ]; then
      printf '%s\n' "$body"
      return 0
    fi
    if [ "$limit" -eq 0 ]; then
      limit=$((total / 2))
    else
      limit=$((limit / 2))
    fi
  done
}

write_comment() {
  local header="$1" create_allowed="$2" existing body
  existing=$(gh api --paginate "repos/$REPO/issues/$PR_NUMBER/comments?per_page=100" |
    jq -r --arg marker "$MARKER" --arg bot "$COMMENT_BOT" \
      '.[] | select(.user.login == $bot and (.body | contains($marker))) | .id' | sed -n 1p)

  if [ -z "$existing" ] && [ "$create_allowed" != "true" ]; then
    echo "no comment: nothing to update"
    return 0
  fi
  if [ "$DRY_RUN" = "true" ]; then
    echo "dry run: would $([ -n "$existing" ] && echo update || echo create) comment"
    return 0
  fi

  body=$(comment_body "$header")
  if [ -n "$existing" ]; then
    if gh api -X PATCH "repos/$REPO/issues/comments/$existing" -f body="$body" >/dev/null; then
      echo "updated comment"
    else
      echo "::warning::Could not update the PR comment"
      echo "comment update failed"
    fi
  elif gh api -X POST "repos/$REPO/issues/$PR_NUMBER/comments" -f body="$body" >/dev/null; then
    echo "created comment"
  else
    echo "::warning::Could not create the PR comment"
    echo "comment create failed"
  fi
}

emit() {
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    cat >>"$GITHUB_STEP_SUMMARY"
  else
    cat
  fi
}

SHAS=$(git rev-list --right-only --cherry-mark --topo-order --reverse "$BASE...$HEAD" |
  { grep -v '^=' || true; } | sed 's/^+//')

if [ -z "$SHAS" ]; then
  printf '## Promotion check\n\nNo commits beyond the base branch. Nothing to check.\n' | emit
  exit 0
fi

while IFS= read -r sha; do
  check_commit "$sha"
done <<<"$SHAS"

TOTAL=$(grep -c . "$ROWS")
PASSED=$(grep -c '^[^|]*|1|' "$ROWS" || true)

if [ "$PASSED" -eq "$TOTAL" ]; then
  VERDICT="All commits pass."
  OUTCOME=$(write_comment "$PASS_HEADER" true)
else
  VERDICT="$((TOTAL - PASSED)) of $TOTAL commits do not pass."
  if [ "$(grep -cF "$NEW_LABEL" "$ROWS" || true)" -eq "$TOTAL" ]; then
    VERDICT="Not a promotion: this PR adds new code."
  fi
  OUTCOME=$(write_comment "$STALE_HEADER" false)
fi

{
  printf '## Promotion check\n\n%s\n\n' "$VERDICT"
  render_table
  printf '\n_%s_\n' "$OUTCOME"
} | emit
