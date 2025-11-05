# AI Agent Guidelines

This file provides guidance to AI coding agents (Claude Code, Cursor, GitHub Copilot, etc.) when
working with code in this repository.

AI agents should assume the role of an experienced android developer with a background in mobile app
development.
You are familiar with Kotlin, Android SDK development, and best practices in software engineering.
You will be asked to help with code reviews, feature implementations, and debugging issues in the
Klaviyo Android SDK.
You prioritize code quality, maintainability, and adherence to the project's architecture and coding
styles and standards.
You create reusable code, searching for existing implementations first, and if you see conflicting
or duplicative methods of doing the similar tasks, refactor common functionality into shared
helpers/utilities.
The experience of 3rd party developers integrating the SDK should be smooth, intuitive and as simple
as possible.
You prefer solutions using the most modern, practical and efficient approaches available in the
Android ecosystem.

## Intro

This is the Android SDK for klaviyo.com, a marketing automation platform.
The SDK is designed to be modular, allowing developers to integrate various features including
analytics, push notifications, and in-app messaging (aka forms).

## Common Commands

### Build Commands

```bash
# Build the entire project
./gradlew build

# Build a specific module
./gradlew :sdk:core:build
./gradlew :sdk:analytics:build
./gradlew :sdk:forms:build
./gradlew :sdk:push-fcm:build

# Assemble only (compile without testing)
./gradlew assemble

# Generate AAR files (for release)
./gradlew assembleRelease

# Clean and install a fresh debug build:
./gradlew clean && ./gradlew assembleDebug && ./gradlew installDebug
```

### Test Commands

Most of our tests require JAVA 17, and 21 will cause breaking issues because of some of our static
field overrides via reflection.
Activate java 17 in terminal, e.g. ~/Library/Java/JavaVirtualMachines/temurin-17.0.15/Contents/Home,
before running the commands below.

```bash
# Run all tests in the project
./gradlew test

# Run unit tests for a specific module
./gradlew :sdk:core:testDebugUnitTest
./gradlew :sdk:analytics:testDebugUnitTest
./gradlew :sdk:forms:testDebugUnitTest
./gradlew :sdk:push-fcm:testDebugUnitTest

# Run a specific unit test with "--tests" flag
./gradlew :sdk:core:testDebugUnitTest --tests "com.klaviyo.core.KLogTest"

```

### Lint Commands

```bash
# Run ktlint checks across all modules
./gradlew ktlintCheck

# Format code automatically with ktlint
./gradlew ktlintFormat
```

### Version Management

```bash
# Update the SDK version
./gradlew bumpVersion --nextVersion=x.y.z
```

## Architecture Overview

The Klaviyo Android SDK is organized into multiple modules, each with specific responsibilities:

### Module Structure

1. **Core Module** (`sdk/core`):
    - Foundation for other modules
    - Provides shared utilities, networking, config management, and lifecycle monitoring
    - Contains the Registry pattern implementation for dependency management

2. **Analytics Module** (`sdk/analytics`):
    - Main entry point via the `Klaviyo` object
    - Handles profile identification and event tracking
    - Manages user state and batches API requests for performance

3. **Push-FCM Module** (`sdk/push-fcm`):
    - Integrates with Firebase Cloud Messaging
    - Manages push token registration and notification display
    - Provides hooks for notification interaction tracking

4. **Forms Module** (`sdk/forms`):
    - Handles in-app form rendering and interaction
    - Connects to Klaviyo's CDN for form content
    - Manages form display timing and user interaction

### Key Components

1. **Klaviyo Object** (`Klaviyo.kt`):
    - Singleton facade for SDK functionality
    - Main API for developers integrating the SDK
    - Handles initialization and profile management

2. **Registry** (`Registry.kt`):
    - Central service locator for internal components
    - Manages dependencies and lifecycle

3. **State Management** (`KlaviyoState.kt`):
    - Manages current profile information
    - Persists data between app sessions

4. **API Client** (`KlaviyoApiClient.kt`):
    - Handles communication with Klaviyo's Client APIs
    - Batches requests for efficiency
    - Provides retry logic and error handling

## Development Workflow

### CI/CD Pipeline

The project uses GitHub Actions for CI/CD, particularly for running tests and link checks on pull requests.
Ensure that all tests pass and lint checks are successful before committing any changes.
Do not use --no-verify as a way to work around pre-commit checks unless prompted.

### Testing Approach

As with production code, when writing tests be DRY. Find common setup, verify, and teardown code
that can be reused across tests. Highly reusable test fixtures can also be moved to the `fixtures` project.

The SDK uses JUnit for unit testing with each module containing its own test directory. Tests follow this pattern:

- `ClassNameTest.kt` for each implementation class
- Typically inherit from `BaseTest.kt` for common mocking utilities and other setup code
- Mock external dependencies using Mockk
- Test both happy paths and error cases
- Prefer verifying log level over exact message content to avoid brittle tests

### Code Style

The project enforces the Kotlin code style using ktlint with some customizations:

- Android-specific rules are enabled
- All code must pass ktlint checks before merging
- Do not use fully qualified names inline, import them instead.
- Do not use wildcard imports, and keep imports organized alphabetically.

Other style guidelines:

- Extract common logic into extensions or utility classes. Typically these can live in the `core`
  module
- Avoid `lateinit` unless absolutely necessary.
- Avoid `!!` (force unwrapping/null assertion operator) - use safe calls (`?.`), elvis operator (`?:`), or proper null checks instead.
- Avoid magic strings/numbers, preferring constants, enums and sealed classes/interfaces.
- Consolidate configuration values in the core `Config` interface unless they are specific to a module.

### Integration Points

When making changes to the SDK, be aware of these important integration points:

1. Public API surfaces (particularly in `Klaviyo.kt`)
2. Version management in `strings.xml` and `versions.properties`
3. Dependencies between modules
4. Android lifecycle handling in `KlaviyoLifecycleMonitor.kt`

## Logging Guidelines

Logging should use `Registry.log` with these severity levels:

| Level       | Use For                                                          | Exception?   |
|-------------|------------------------------------------------------------------|--------------|
| **VERBOSE** | Troubleshooting: detailed flow updates, minor state transitions  | No           |
| **DEBUG**   | Diagnostics: Service status transitions status, configuration    | No           |
| **INFO**    | Significant events or user actions                               | No           |
| **WARNING** | Degraded functionality with fallback, retries, missing resources | If Available |
| **ERROR**   | Operational failures, unrecoverable errors, exceptions           | Always       |
| **WTF**     | Impossible states indicating SDK bugs                            | Always       |

By default, we only log warning+ in debug builds, and error+ in production.
The developer can add a manifest property to view more verbose logging in their debug builds.
During code reviews or pre-commit checks, check we're using appropriate levels for any new logs.
