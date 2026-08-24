#!/bin/bash
set -e

PLATFORM="$1"

if [ -z "$PLATFORM" ]; then
    echo "Usage: $0 <linux|windows>"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$FRONTEND_DIR")"
APP_DIR="$PROJECT_ROOT/app"
TAURI_OUTPUT_DIR="$FRONTEND_DIR/src-tauri/target/release/bundle"
RELEASE_DIR="$FRONTEND_DIR/src-tauri/target/release"

mkdir -p "$APP_DIR/$PLATFORM"

if [ "$PLATFORM" = "linux" ]; then
    if [ -d "$TAURI_OUTPUT_DIR/deb" ]; then
        cp -r "$TAURI_OUTPUT_DIR/deb/"* "$APP_DIR/linux/" 2>/dev/null || true
    fi
    if [ -d "$TAURI_OUTPUT_DIR/appimage" ]; then
        cp -r "$TAURI_OUTPUT_DIR/appimage/"* "$APP_DIR/linux/" 2>/dev/null || true
    fi
    if [ -d "$TAURI_OUTPUT_DIR/rpm" ]; then
        cp -r "$TAURI_OUTPUT_DIR/rpm/"* "$APP_DIR/linux/" 2>/dev/null || true
    fi
    BINARY_NAME=$(ls "$RELEASE_DIR"/zhixue-engine 2>/dev/null || ls "$RELEASE_DIR"/智学引擎 2>/dev/null || true)
    if [ -n "$BINARY_NAME" ]; then
        cp "$BINARY_NAME" "$APP_DIR/linux/" 2>/dev/null || true
    fi
    echo "Linux artifacts copied to $APP_DIR/linux/"
elif [ "$PLATFORM" = "windows" ]; then
    if [ -d "$TAURI_OUTPUT_DIR/msi" ]; then
        cp -r "$TAURI_OUTPUT_DIR/msi/"* "$APP_DIR/windows/" 2>/dev/null || true
    fi
    if [ -d "$TAURI_OUTPUT_DIR/nsis" ]; then
        cp -r "$TAURI_OUTPUT_DIR/nsis/"* "$APP_DIR/windows/" 2>/dev/null || true
    fi
    EXE_NAME=$(ls "$RELEASE_DIR"/zhixue-engine.exe 2>/dev/null || ls "$RELEASE_DIR"/智学引擎.exe 2>/dev/null || true)
    if [ -n "$EXE_NAME" ]; then
        cp "$EXE_NAME" "$APP_DIR/windows/" 2>/dev/null || true
    fi
    echo "Windows artifacts copied to $APP_DIR/windows/"
fi

echo "Done! Artifacts are in $APP_DIR/$PLATFORM/"
