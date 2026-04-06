#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

CONTAINER_RUNTIME="${CONTAINER_RUNTIME:-docker}"
IMAGE_NAME="sparkle4j-desktop-tests"

echo "Building desktop test image with $CONTAINER_RUNTIME..."
"$CONTAINER_RUNTIME" build \
    -f "$SCRIPT_DIR/Dockerfile.desktop-tests" \
    -t "$IMAGE_NAME" \
    "$PROJECT_DIR"

echo "Running desktop tests..."
"$CONTAINER_RUNTIME" run --rm "$IMAGE_NAME"
