# Bugbot Configuration

## Primary Instructions

**Read the main AI agent guidelines in `/AGENTS.md`** - this file contains comprehensive guidance on:
- Project architecture and module structure
- Build, test, and lint commands
- Code style and conventions
- **Logging level guidelines** (critical for PR reviews)
- Testing best practices

## Additional Bugbot-Specific Rules

### Focus Areas for PR Reviews

1. **Log Level Appropriateness**
   - Verify log statements use the correct severity level per AGENTS.md guidelines
   - Flag VERBOSE/DEBUG logs that should be INFO or higher
   - Flag INFO logs for routine operations that should be VERBOSE
   - Ensure exceptions are included with WARNING/ERROR/WTF logs

2. **Test Quality**
   - Tests should verify log **level**, not exact message content (avoid brittle tests)
   - Look for `verify { spyLog.exact("message") }` and suggest `verify { spyLog.level(any()) }`

3. **Code Quality**
   - All code must pass ktlintCheck before merge
   - No wildcard imports
   - Avoid lateinit unless necessary
   - Extract duplicated logic into shared utilities

4. **Security**
   - No sensitive user data (emails, tokens, PII) in log messages
   - Proper exception handling in public API surfaces

### Common Issues to Flag

- Log statements at wrong severity level
- Test assertions checking exact log messages instead of log level
- Code style violations that ktlint would catch
- Missing exception parameters in error logs
- Duplicated code that could be refactored
