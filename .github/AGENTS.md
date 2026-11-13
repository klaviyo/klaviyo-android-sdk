# .github Guidelines

For agents and humans editing workflows and actions here. `CLAUDE.md` in this directory points to this file.

## Slack

- Use only `.github/actions/notify-slack` for Slack. It wraps slackapi/slack-github-action, so don't call slackapi or curl directly.
- `token: ${{ secrets.SLACK_BOT_TOKEN }}`. An empty token skips, so no presence checks.
- One message per release or deploy, updated through `ts` (job output or dispatch input). Failure details go in a `thread_ts` reply.
- Each workflow sets `SLACK_USERNAME` and `SLACK_ICON_EMOJI` in its top-level `env:` ("Android SDK", `:merlin-orb-android:`).
- `continue-on-error: true` on Slack notify steps and Slack-action checkout steps.
- Report a cancelled job only on timeout: the `Classify cancellation` step in `instrumented-tests.yml` and `jitpack.yml`.
- `publish-release.yml` passes `slack_thread_ts` to `jitpack.yml` and `doc-bot.yml`, only when the workflow at the tag declares the input. Both announce a `pending` message when they start (a reply in the release thread when `slack_thread_ts` is set, otherwise a new message), edit it with `ts` at stage changes, and end with one final edit: published, failed (naming the failed step), blocked (version mismatch), or stopped. `doc-bot.yml` polls the Pages build API (up to 10 minutes) with the job's `GITHUB_TOKEN` under `pages: read`, and ends with docs live, Pages build failed, or stopped when the run was cancelled.
- `publish-release.yml`'s `stopped` job reports "approval rejected or expired" when the `publish` job never started. `jitpack.yml` announces only tag builds and posts no success message for push events (failures still post). Jobs on another ref sparse-checkout `.github/actions` into `_slack/`.
- `notify-slack` stays byte-identical in all 7 repos: klaviyo-android-sdk, klaviyo-android-test-app, klaviyo-swift-sdk, klaviyo-ios-test-app, klaviyo-react-native-sdk, klaviyo-expo-plugin, klaviyo-flutter-sdk.
- Backtick branches, tags, versions, file paths, workflow names, env and secret names, and SHAs in titles, bodies and field values. Show 7-character SHAs, with a `Commit` link. The one exception is the JitPack version for a branch build, which is the full SHA users paste into Gradle, so it goes backticked in `body`.

## Workflow conventions

- Pin actions by tag. Use a full SHA with a `# vX.Y.Z` comment for actions that handle credentials or release data (`linear/linear-release-action`, `slackapi/slack-github-action`).
- `publish-release.yml` and `jitpack.yml` use `concurrency` groups with `cancel-in-progress: false`, except `jitpack.yml` pushes to `master`, which share one `jitpack-master` group that cancels the older run.

## Promotion check

- `promotion-check.yml` runs `.github/scripts/promotion-check.sh` on every PR. It is informational, so never add it as a required check.
- A commit's source PR counts as reviewed only on an APPROVED review from someone other than the author and not a bot, who has `admin`, `maintain`, or `write` repo access according to the collaborator permission API. Repo access is what qualifies an approver, not org membership or `author_association`; the workflow's `GITHUB_TOKEN` cannot see private org membership.
- A source PR merged without a qualifying approval passes when every file it changed is unowned in `CODEOWNERS` at the base revision, because rulesets then require no review. CODEOWNERS lookup follows GitHub's `.github/`, root, then `docs/` order; no file means every path is owned.
