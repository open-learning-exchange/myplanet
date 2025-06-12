# Contributor Guidelines

This repository uses specific conventions for branches, commit messages, and pull requests. Follow these rules when contributing via Codex:

## Branch naming
- Use `issueNumber-short-description` (e.g. `5934-fix-login-screen`).

## Commit messages
- Start with a component name followed by a colon and a short summary.
- Use the imperative mood (e.g. `sync: improve login flow`).
- If the commit resolves an issue, append `fixes #<issue>`.

## Pull requests
- Title should mirror the commit message format and the summary must start with "smoother": `<component>: smoother <summary> (fixes #issue)`.
- PR body must include `(fixes #<issue number>)` on its own line and a brief explanation of the change.
- Always link a well-described issue. Create one with `gh issue create` if needed before opening the PR.
- Target the `master` branch.
- After creating the PR, trigger a GitHub Actions build with `gh workflow run`.
- Verify `gh` is installed by running `gh --version` before executing any GitHub CLI steps.

## Testing
- No automated tests are defined. Verify the build with `./gradlew assembleDebug --dry-run` before submitting.
