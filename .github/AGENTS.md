# .github Guidelines

For agents and humans editing workflows and actions here. `CLAUDE.md` in this directory points to this file.

## Slack

- Post to Slack only through `.github/actions/notify-slack`. Never use slackapi/slack-github-action or raw curl.
- Pass the bot token from the `SLACK_BOT_TOKEN` repository secret: `token: ${{ secrets.SLACK_BOT_TOKEN }}`. A composite action cannot read secrets. An empty token skips the post, so do not add secret-presence checks.
- One message per release or deploy. Post once, expose the `ts` output as a job output or workflow_dispatch input, and let later stages update that message with `ts`. Put failure details in a reply with `thread_ts`.
- Cross-workflow replies: `publish-release.yml` passes `slack_thread_ts` to `jitpack.yml`. Leave it empty to post fresh.
- A job that uses a local action needs a checkout first. Jobs that check out another ref use a sparse checkout of `.github/actions` into `_slack/`.
- `notify-slack` must stay byte-identical in all 7 MAGE repos: klaviyo-android-sdk, klaviyo-android-test-app, klaviyo-swift-sdk, klaviyo-ios-test-app, klaviyo-react-native-sdk, klaviyo-expo-plugin, klaviyo-flutter-sdk. Change it in all of them together.

## Workflow conventions

- Pin third-party actions by tag (`actions/checkout@v4`). Pin actions that handle credentials or release data by full commit SHA with a `# vX.Y.Z` comment, as `linear/linear-release-action` is.
- Declare `permissions:` with `contents: read` at the top of workflows that do more than build, and raise it per job only for what that job needs (`contents: write`, `pull-requests: write`). New workflows should declare it.
- Add a `concurrency` group to workflows that must not overlap (`publish-release.yml`, `jitpack.yml`). Use `cancel-in-progress: false` for releases.
- Give anything useful to run by hand a `workflow_dispatch` trigger.
- Never echo secrets. Pass them through `env:`, not inline in `run:`. Pass untrusted values (branch names, titles, tags) through `env:` too.
- Use `failure()` for failure handling, `success()` for success-only steps, and `!cancelled()` for steps that must run unless the run was cancelled. Add `continue-on-error: true` to notification steps.
- Slack steps must never fail the job.

## Testing workflow changes

- Run `actionlint .github/workflows/*.yml` before pushing.
- Run `shellcheck` on any script you touch.
- Trigger the workflow with `workflow_dispatch` from your branch, or use a `dry_run` input where one exists (`publish-release.yml`).
