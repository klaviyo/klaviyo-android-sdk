# Bugbot Configuration

## Primary Instructions

**Read the main AI agent guidelines in `/AGENTS.md`** - this file contains comprehensive guidance on:
- Project architecture and module structure
- Build, test, and lint commands
- Code style and conventions
- Testing best practices

## Additional Bugbot-Specific Rules
- Avoid wildcard imports
- Avoid fully qualified names inline
- Avoid lateinit unless absolutely necessary
- Extract duplicated logic into shared utilities
- Proper exception handling in public API surfaces
- Verify log statements use the correct severity level per AGENTS.md guidelines
- Tests should generally verify log level, not exact message strings to avoid brittle tests
