#!/usr/bin/env bash
set -euo pipefail

# ---- Config (edit once, reuse forever) ----
PROJECT="myplanet-ci"
BUCKET="myplanet-test-results"
DEVICE="model=Pixel2.arm,version=29,locale=en,orientation=portrait"
TIMEOUT="12m"
RECORD_VIDEO="--record-video"

# Paths (relative to repo root; override via env if you like)
APK="${APK:-$(pwd)/app/build/outputs/apk/default/debug/app-default-debug.apk}"
SCRIPT="${SCRIPT:-$(pwd)/ci/robo/myplanet_robo.json}"

# ---- Build (optional; comment out if your APK already exists) ----
if [ ! -f "$APK" ]; then
  echo "APK not found at $APK — building :app:assembleDebug..."
  ./gradlew :app:assembleDebug
fi

# ---- Sanity checks ----
[ -f "$APK" ] || { echo "Missing APK: $APK"; exit 1; }
[ -f "$SCRIPT" ] || { echo "Missing Robo script: $SCRIPT"; exit 1; }

# ---- GCloud project ----
gcloud config set project "$PROJECT" >/dev/null

# ---- Compute output dir + axis dir (for public URLs) ----
RUN_DIR="local-test/$(date +%Y%m%d_%H%M%S)"
# Parse DEVICE into Firebase axis folder name: <model>-<version>-<locale>-<orientation>
MODEL=$(echo "$DEVICE" | sed -n 's/.*model=\([^,]*\).*/\1/p')
VERSION=$(echo "$DEVICE" | sed -n 's/.*version=\([^,]*\).*/\1/p')
LOCALE=$(echo "$DEVICE" | sed -n 's/.*locale=\([^,]*\).*/\1/p')
ORIENTATION=$(echo "$DEVICE" | sed -n 's/.*orientation=\([^,]*\).*/\1/p')
AXIS_DIR="${MODEL:-device}-${VERSION:-ver}-${LOCALE:-loc}-${ORIENTATION:-ori}"

echo "Starting Firebase Test Lab Robo run..."

# ---- Run test (don't exit on non-zero so we can still print links) ----
set +e
gcloud firebase test android run \
  --type robo \
  --app "$APK" \
  --robo-script "$SCRIPT" \
  --device "$DEVICE" \
  --results-bucket "$BUCKET" \
  --results-dir "$RUN_DIR" \
  --timeout "$TIMEOUT" \
  $RECORD_VIDEO
EXIT_CODE=$?
set -e

# ---- Print handy links ----
echo
echo "Artifacts (GCS console):"
echo "  https://console.developers.google.com/storage/browser/${BUCKET}/${RUN_DIR}/"
echo
echo "Public result links (if bucket is public):"
echo "  Folder:  https://storage.googleapis.com/${BUCKET}/${RUN_DIR}/"
echo "  Video:   https://storage.googleapis.com/${BUCKET}/${RUN_DIR}/${AXIS_DIR}/video.mp4"
echo "  Logcat:  https://storage.googleapis.com/${BUCKET}/${RUN_DIR}/${AXIS_DIR}/logcat"
echo "  Actions: https://storage.googleapis.com/${BUCKET}/${RUN_DIR}/${AXIS_DIR}/actions.json"
echo "  APK:     https://storage.googleapis.com/${BUCKET}/${RUN_DIR}/app-default-debug.apk"
echo
echo "Firebase Test UI (also printed by gcloud above):"
echo "  https://console.firebase.google.com/project/${PROJECT}/testlab/histories"
echo

# ---- Exit with original gcloud status (so CI can detect failures) ----
if [ "$EXIT_CODE" -ne 0 ]; then
  echo "Note: gcloud exited with code ${EXIT_CODE} (tests may have failed or app crashed)."
fi
exit "$EXIT_CODE"
