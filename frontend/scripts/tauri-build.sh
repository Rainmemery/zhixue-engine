#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$(dirname "$SCRIPT_DIR")"
APP_DIR="$(dirname "$FRONTEND_DIR")/app"
ADMIN_DIR="$FRONTEND_DIR/app/admin"
ADMIN_BACKUP="$FRONTEND_DIR/app/_admin_disabled"

cleanup() {
    if [ -d "$ADMIN_BACKUP" ]; then
        mv "$ADMIN_BACKUP" "$ADMIN_DIR" 2>/dev/null || true
    fi
}

trap cleanup EXIT

if [ -d "$ADMIN_DIR" ]; then
    mv "$ADMIN_DIR" "$ADMIN_BACKUP"
fi

export TAURI_BUILD=true
cd "$FRONTEND_DIR"
npx next build

if [ -d "$ADMIN_BACKUP" ]; then
    mv "$ADMIN_BACKUP" "$ADMIN_DIR"
fi

trap - EXIT

echo "Static export completed successfully!"
echo "Output directory: $FRONTEND_DIR/out"
