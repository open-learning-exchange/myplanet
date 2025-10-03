#!/usr/bin/env bash
set -euo pipefail

# ---- Config (edit once, reuse forever) ----
PROJECT="myplanet-ci"
BUCKET="myplanet-test-results"
DEVICE="model=Pixel2.arm,version=29,locale=en,orientation=portrait"
TIMEOUT="12m"
RECORD_VIDEO="--record-video"

# Paths (relative to repo root)
APK="${APK:-$(pwd)/app/build/outputs/apk/default/debug/app-default-debug.apk}"
SCRIPT="${SCRIPT:-$(pwd)/ci/robo/myplanet_robo.json}"

# ---- Build (optional; comment out if your APK already exists) ----
if [ ! -f "$APK" ]; then
  echo "APK not found at $APK — building assembleDebug..."
  ./gradlew :app:assembleDebug
fi

# ---- Sanity checks ----
[ -f "$APK" ] || { echo "Missing APK: $APK"; exit 1; }
[ -f "$SCRIPT" ] || { echo "Missing Robo script: $SCRIPT"; exit 1; }

# ---- GCloud project ----
gcloud config set project "$PROJECT" >/dev/null

# ---- Run test ----
RUN_DIR="local-test/$(date +%Y%m%d_%H%M%S)"
echo "Starting Firebase Test Lab Robo run..."
gcloud firebase test android run \
  --type robo \
  --app "$APK" \
  --robo-script "$SCRIPT" \
  --device "$DEVICE" \
  --results-bucket "$BUCKET" \
  --results-dir "$RUN_DIR" \
  --timeout "$TIMEOUT" \
  $RECORD_VIDEO

echo
echo "Artifacts (if bucket is public):"
echo "  APK:     https://storage.googleapis.com/${BUCKET}/${RUN_DIR}/app-default-debug.apk"
echo "  Results: https://console.developers.google.com/storage/browser/${BUCKET}/${RUN_DIR}/"
echo "  Test UI: (see console link printed by gcloud above)"
