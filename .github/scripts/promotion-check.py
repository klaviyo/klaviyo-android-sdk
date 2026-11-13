#!/usr/bin/env python3
"""Promotion check: prove every commit in a PR was already reviewed in its own PR.

Reads from the git checkout (commit range) and the GitHub API via the `gh` CLI
(authenticated through GH_TOKEN). Writes a markdown table to $GITHUB_STEP_SUMMARY
(or stdout) and, when every commit passes, keeps one marker comment on the PR.
Exits non-zero only when the script itself fails.
"""

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

MARKER = "<!-- promotion-check -->"
BOT_COMMENT_LOGIN = "github-actions[bot]"
EXEMPT_MERGER = "evan-masseau"
COMMENT_LIMIT = 60000
MAX_AFTER_SHAS = 5

PASS_HEADER = (
    "✅ Every commit in this PR was already reviewed in its own PR. OK to admin merge."
)
STALE_HEADER = (
    "⚠️ This PR no longer passes the promotion check. "
    "Not every commit was already reviewed in its own PR."
)


def run(cmd, check=True):
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if check and proc.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} failed: {proc.stderr.strip()}")
    return proc.stdout


def gh_api(path, paginate=False, method="GET", fields=None):
    cmd = ["gh", "api", "-X", method]
    if paginate:
        cmd.append("--paginate")
    for key, value in (fields or {}).items():
        cmd += ["-f", f"{key}={value}"]
    cmd.append(path)
    out = run(cmd)
    if not paginate:
        return json.loads(out) if out.strip() else None
    decoder = json.JSONDecoder()
    items, pos = [], 0
    while pos < len(out):
        while pos < len(out) and out[pos].isspace():
            pos += 1
        if pos >= len(out):
            break
        chunk, pos = decoder.raw_decode(out, pos)
        items.extend(chunk if isinstance(chunk, list) else [chunk])
    return items


def load_team(path):
    team = set()
    for line in Path(path).read_text().splitlines():
        login = line.split("#", 1)[0].strip().lower()
        if login:
            team.add(login)
    return team


def is_bot(user):
    return user["login"].endswith("[bot]") or user.get("type") == "Bot"


def commit_set(base, head):
    out = run(
        ["git", "rev-list", "--right-only", "--cherry-mark", "--topo-order", f"{base}...{head}"]
    )
    shas = []
    for line in out.splitlines():
        if line.startswith("="):
            continue
        shas.append(line.lstrip("+").strip())
    return list(reversed(shas))


def parent_count(sha):
    return len(run(["git", "rev-list", "--parents", "-n", "1", sha]).split()) - 1


class Checker:
    def __init__(self, repo, current_pr, team):
        self.repo = repo
        self.current_pr = current_pr
        self.team = team
        self.pr_cache = {}

    def pr_url(self, number):
        return f"https://github.com/{self.repo}/pull/{number}"

    def evaluate_pr(self, number):
        if number in self.pr_cache:
            return self.pr_cache[number]
        pr = gh_api(f"repos/{self.repo}/pulls/{number}")
        author = pr["user"]["login"]
        reviews = gh_api(f"repos/{self.repo}/pulls/{number}/reviews?per_page=100", paginate=True)
        qualifying = []
        for review in reviews:
            user = review.get("user")
            if review.get("state") != "APPROVED" or not user:
                continue
            login = user["login"]
            if login.lower() == author.lower() or is_bot(user):
                continue
            if login.lower() not in self.team:
                continue
            qualifying.append(review)
        merged_by = (pr.get("merged_by") or {}).get("login")
        result = {
            "number": number,
            "author": author,
            "merged_by": merged_by,
            "approvers": sorted({r["user"]["login"] for r in qualifying}),
            "status": "approved" if qualifying else "none",
            "after": [],
            "after_known": True,
        }
        if qualifying:
            latest = max(qualifying, key=lambda r: r.get("submitted_at") or "")
            pr_commits = gh_api(
                f"repos/{self.repo}/pulls/{number}/commits?per_page=100", paginate=True
            )
            shas = [c["sha"] for c in pr_commits]
            if latest.get("commit_id") in shas:
                result["after"] = shas[shas.index(latest["commit_id"]) + 1 :]
            else:
                result["after_known"] = False
        elif merged_by and merged_by.lower() == EXEMPT_MERGER:
            result["status"] = "exempt"
        self.pr_cache[number] = result
        return result

    def check_commit(self, sha):
        row = {"sha": sha, "pr": None, "ok": False, "detail": "", "after": ""}
        if parent_count(sha) > 1:
            diff = run(["git", "show", "--remerge-diff", "--format=", sha])
            if diff.strip():
                row["detail"] = "merge commit introduces changes beyond its parents"
            else:
                row["ok"] = True
                row["detail"] = "merge commit, nothing beyond its parents"
            return row

        pulls = gh_api(f"repos/{self.repo}/commits/{sha}/pulls?per_page=100", paginate=True)
        numbers = sorted(
            p["number"]
            for p in pulls
            if p.get("merged_at") and p["number"] != self.current_pr
        )
        if not numbers:
            row["detail"] = "no merged source PR (direct push)"
            return row

        results = [self.evaluate_pr(n) for n in numbers]
        chosen = next((r for r in results if r["status"] == "approved"), None)
        chosen = chosen or next((r for r in results if r["status"] == "exempt"), None)
        chosen = chosen or results[0]
        row["pr"] = chosen["number"]

        if chosen["status"] == "approved":
            row["ok"] = True
            row["detail"] = ", ".join(f"@{a}" for a in chosen["approvers"])
            if not chosen["after_known"]:
                row["after"] = "approved commit no longer in PR history"
            else:
                after = chosen["after"]
                shown = ", ".join(f"`{s[:7]}`" for s in after[:MAX_AFTER_SHAS])
                if len(after) > MAX_AFTER_SHAS:
                    shown += ", …"
                row["after"] = f"{len(after)} commits after approval" + (
                    f" ({shown})" if after else ""
                )
        elif chosen["status"] == "exempt":
            row["ok"] = True
            row["detail"] = f"exempt: admin merge by {EXEMPT_MERGER}"
        else:
            by = chosen["merged_by"] or "unknown"
            row["detail"] = f"merged by @{by} without a qualifying approval"
        return row


def render_table(rows, repo, limit=None):
    lines = [
        "| Commit | Source PR | Approver / reason | After approval |",
        "| --- | --- | --- | --- |",
    ]
    shown = rows if limit is None else rows[:limit]
    for row in shown:
        mark = "✅" if row["ok"] else "❌"
        pr = (
            f"[#{row['pr']}](https://github.com/{repo}/pull/{row['pr']})"
            if row["pr"]
            else "none"
        )
        lines.append(
            f"| {mark} `{row['sha'][:7]}` | {pr} | {row['detail']} | {row['after']} |"
        )
    if limit is not None and len(rows) > limit:
        lines.append(f"| … | | {len(rows) - limit} more rows, see the job summary | |")
    return "\n".join(lines)


def comment_body(header, rows, repo):
    limit = None
    while True:
        body = f"{MARKER}\n{header}\n\n{render_table(rows, repo, limit)}\n"
        if len(body) <= COMMENT_LIMIT:
            return body
        limit = (len(rows) if limit is None else limit) // 2


def find_existing_comment(repo, pr_number):
    comments = gh_api(f"repos/{repo}/issues/{pr_number}/comments?per_page=100", paginate=True)
    for comment in comments:
        if MARKER in (comment.get("body") or "") and comment["user"]["login"] == BOT_COMMENT_LOGIN:
            return comment["id"]
    return None


def post_comment(repo, pr_number, body, dry_run, create_allowed):
    existing = find_existing_comment(repo, pr_number)
    if existing is None and not create_allowed:
        return "no comment: nothing to update"
    if dry_run:
        return f"dry run: would {'update' if existing else 'create'} comment"
    try:
        if existing:
            gh_api(
                f"repos/{repo}/issues/comments/{existing}",
                method="PATCH",
                fields={"body": body},
            )
            return "updated comment"
        gh_api(
            f"repos/{repo}/issues/{pr_number}/comments",
            method="POST",
            fields={"body": body},
        )
        return "created comment"
    except RuntimeError as err:
        print(f"::warning::Could not write PR comment: {err}")
        return "comment failed (token may be read-only for this PR)"


def write_summary(text):
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a", encoding="utf-8") as fh:
            fh.write(text + "\n")
    else:
        print(text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, help="owner/name")
    parser.add_argument("--pr", required=True, type=int, help="PR number")
    parser.add_argument("--base", required=True, help="base ref or sha")
    parser.add_argument("--head", required=True, help="head ref or sha")
    parser.add_argument("--author", help="PR author login (looked up when omitted)")
    parser.add_argument(
        "--team-file",
        default=str(Path(__file__).resolve().parent.parent / "promotion-check-team.txt"),
    )
    parser.add_argument("--dry-run", action="store_true", help="never write a PR comment")
    args = parser.parse_args()

    team = load_team(args.team_file)
    author = args.author or gh_api(f"repos/{args.repo}/pulls/{args.pr}")["user"]["login"]

    shas = commit_set(args.base, args.head)
    if not shas:
        write_summary("## Promotion check\n\nNo commits beyond the base branch. Nothing to check.")
        return

    checker = Checker(args.repo, args.pr, team)
    rows = [checker.check_commit(sha) for sha in shas]
    all_pass = all(r["ok"] for r in rows)
    author_on_team = author.lower() in team
    passed = sum(r["ok"] for r in rows)

    verdict = (
        "All commits pass."
        if all_pass
        else f"{len(rows) - passed} of {len(rows)} commits do not pass."
    )
    notes = []
    if all_pass and not author_on_team:
        notes.append(f"PR author @{author} is not on the team, so no comment is posted.")
    summary = f"## Promotion check\n\n{verdict}\n\n{render_table(rows, args.repo)}"
    if notes:
        summary += "\n\n" + "\n".join(notes)

    qualifies = all_pass and author_on_team
    header = PASS_HEADER if qualifies else STALE_HEADER
    outcome = post_comment(
        args.repo,
        args.pr,
        comment_body(header, rows, args.repo),
        args.dry_run,
        create_allowed=qualifies,
    )
    summary += f"\n\n_{outcome}_"
    write_summary(summary)


if __name__ == "__main__":
    try:
        main()
    except Exception as err:  # noqa: BLE001
        print(f"::error::promotion-check failed: {err}", file=sys.stderr)
        sys.exit(1)
