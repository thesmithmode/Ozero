# Git Hooks

This directory contains custom git hooks for the Ozero project to enforce code quality and security checks.

## Hooks

### `pre-commit`

Runs automatically before each commit to ensure:

1. **ktlint check** — Validates Kotlin code formatting against project standards
2. **detekt analysis** — Detects code quality issues (complexity, potential bugs, style violations)
3. **gitleaks detection** — Scans for accidentally committed secrets (optional)

## Setup

To enable these hooks in your local repository:

### Option 1: Automatic Setup (Recommended)
```bash
./.githooks/install.sh
```

### Option 2: Manual Setup
```bash
git config core.hooksPath .githooks
chmod +x .githooks/*
```

## Bypass (Emergency Only)

If you need to bypass pre-commit checks temporarily:

```bash
git commit --no-verify
```

**Note**: Always review why the hook failed. Use `--no-verify` only in exceptional circumstances.

## Fixing Violations

### ktlint formatting
Auto-fix most issues:
```bash
./gradlew ktlintFormat
```

### detekt issues
Review the report in:
```
build/reports/detekt/detekt.html
```

Then manually fix the issues or add suppressions where appropriate.

### gitleaks secrets
Remove the secret from files and git history, then re-stage:
```bash
# Remove from current files
# Remove from git history: git filter-branch, BFG, or similar
git add .
git commit
```

## Documentation

- [detekt Configuration](../detekt.yml)
- [ktlint Documentation](https://pinterest.github.io/ktlint/)
- [Gradle Ktlint Plugin](https://github.com/jlleitschuh/ktlint-gradle)
