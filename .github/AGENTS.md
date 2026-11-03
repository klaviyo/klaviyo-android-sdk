# .github Guidelines

For agents and humans editing workflows and actions here. `CLAUDE.md` in this directory points to this file.

## Slack

- Use only `.github/actions/notify-slack` for Slack. It wraps slackapi/slack-github-action, so don't call slackapi or curl directly.
- `token: ${{ secrets.SLACK_BOT_TOKEN }}`. An empty token skips, so no presence checks.
- One message per release or deploy, updated through `ts` (job output or dispatch input). Failure details go in a `thread_ts` reply.
- Each workflow sets `SLACK_USERNAME` and `SLACK_ICON_EMOJI` in its top-level `env:` ("Android SDK", `:merlin-orb-android:`).
- `continue-on-error: true` on Slack notify steps and Slack-action checkout steps.
- Report a cancelled job only on timeout: the `Classify cancellation` step in `instrumented-tests.yml` and `jitpack.yml`.
- `publish-release.yml` passes `slack_thread_ts` to `jitpack.yml`, only when the workflow at the tag declares the input. Jobs on another ref sparse-checkout `.github/actions` into `_slack/`.
- `notify-slack` stays byte-identical in all 7 repos: klaviyo-android-sdk, klaviyo-android-test-app, klaviyo-swift-sdk, klaviyo-ios-test-app, klaviyo-react-native-sdk, klaviyo-expo-plugin, klaviyo-flutter-sdk.

## Workflow conventions

- Pin actions by tag. Use a full SHA with a `# vX.Y.Z` comment for actions that handle credentials or release data (`linear/linear-release-action`, `slackapi/slack-github-action`).
- `publish-release.yml` and `jitpack.yml` use `concurrency` groups with `cancel-in-progress: false`.
