#!/usr/bin/env bash
# Install git hooks for the Ozero project

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

echo "Installing git hooks..."

# Configure git to use .githooks directory
git config core.hooksPath .githooks
echo "✓ Git configured to use .githooks"

# Make hooks executable
chmod +x .githooks/*
echo "✓ Hooks made executable"

echo ""
echo "Git hooks installed successfully!"
echo "Hooks will run automatically on git commit."
echo ""
echo "To verify: git config core.hooksPath"
