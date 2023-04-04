# Welcome to Klaviyo SDK contributing guide

Thank you for considering contributing to the Klaviyo Android SDK!

Read our [Code of Conduct](./CODE_OF_CONDUCT.md) to keep our community approachable and respectable.

In this guide you will get an overview of the contribution workflow from engaging in discussion to 
opening an issue, creating a PR, reviewing, and merging the PR.

## New contributor guide

To get an overview of the project, read the [README](README.md). 
Here are some additional resources to help you get started:

- [Engage in Discussions](https://docs.github.com/en/discussions/collaborating-with-your-community-using-discussions/participating-in-a-discussion)
- [Finding ways to contribute to open source on GitHub](https://docs.github.com/en/get-started/exploring-projects-on-github/finding-ways-to-contribute-to-open-source-on-github)
- [Set up Git](https://docs.github.com/en/get-started/quickstart/set-up-git)
- [GitHub flow](https://docs.github.com/en/get-started/quickstart/github-flow)
- [Collaborating with pull requests](https://docs.github.com/en/github/collaborating-with-pull-requests)

### Discussions and questions

Discussions are enabled for this repo in order to reduce the friction of getting set up and using
the SDK in your projects. Don't hesitate to [dive in](https://github.com/klaviyo/klaviyo-android-sdk/discussions) 
to the discussions, or open a new topic if you don't see what you're looking for.

### Create a new issue

If you spot a problem, or want to suggest a new feature, first 
[search if an issue already exists](https://docs.github.com/en/github/searching-for-information-on-github/searching-on-github/searching-issues-and-pull-requests#search-by-the-title-body-or-comments).
If a related issue doesn't exist, you can open a new issue using a relevant [issue form](https://github.com/klaviyo/klaviyo-android-sdk/issues/new/choose).

### Solve an issue

If you want to recommend a code fix for an existing issue, you are welcome to open a PR with a fix.

1. Fork the repository and clone to your machine, open in Android Studio 
2. Once the project has synced, run `./gradlew addKtlintFormatGitPreCommitHook` to install our
   pre-commit code formatting rules. 
3. We recommend selecting the `productionDebug` build variant to make use of our debug logging.
4. Make your changes to the SDK. While we encourage test-driven development, we will not require 
   unit tests to submit a PR. That said, tests are an easy way to verify your changes as you go. 
   We have a very high coverage rate, so there are plenty of examples to follow.
5. We also encourage you to test your changes against your own app. First, add [mavenLocal](https://docs.gradle.org/current/userguide/declaring_repositories.html#sec:case-for-maven-local) 
   as the first repository in your gradle file. Then run the following command to compile a local copy 
   of the SDK for your app to consume. *Remember to remove `mavenLocal` when you're finished.*
   ```
   .gradlew publishToMavenLocal
   ``` 
7. Commit the changes once you are happy with them, please include a detailed commit message. 

### Pull Request

When you're finished with the changes, create a pull request, also known as a PR.
- Fill the template so that we can review your PR. This template helps reviewers
  understand your changes and the purpose of your pull request.
- Don't forget to [link the PR to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue) 
  if you are solving one.
- Enable the checkbox to [allow maintainer edits](https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/allowing-changes-to-a-pull-request-branch-created-from-a-fork) 
  so the branch can be updated for a merge. Once you submit your PR, a team member will review your 
  proposal. We may ask questions or request additional information.
- We may ask for changes to be made before a PR can be merged, either using [suggested changes](https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/incorporating-feedback-in-your-pull-request) 
  or pull request comments.  
- As you update your PR and apply changes, mark each conversation as [resolved](https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/commenting-on-a-pull-request#resolving-conversations).
- Alternatively, we may incorporate your suggestions into another feature branch and communicate 
  progress in the original issue or PR. 
