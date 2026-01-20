#!/bin/bash
set -e

SWAGGER_FILE="generator/swagger/telegram-bot-api.json"
TAG="v${VERSION}"

# Check if release exists
if ! gh release view "$TAG" >/dev/null 2>&1; then
  echo "No release found for $TAG, creating new release"
  gh release create "$TAG" "$SWAGGER_FILE" \
    --title "Telegram Bot API v${VERSION}" \
    --notes "Telegram Bot API Swagger specification v${VERSION}

Generated on: $(date -u +"%Y-%m-%d %H:%M:%S UTC")"
  echo "Release $TAG created successfully"
else
  echo "Release $TAG already exists, no update needed"
fi
