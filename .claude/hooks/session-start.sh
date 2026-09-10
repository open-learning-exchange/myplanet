#!/bin/bash
# Provisions the Flutter toolchain for the `flutter/` port so that
# `dart format`, `flutter analyze` and `flutter test` behave here exactly as
# they do in `.github/workflows/flutter.yml`.
#
# Two things make this worth doing at session start rather than on demand:
#
#  1. The SDK version is load-bearing. `dart format` output differs between
#     Dart releases, and CI gates on `--set-exit-if-changed`. A session that
#     formats with a newer SDK produces a diff CI rejects even though the code
#     looks correct locally. Pin to the same version CI uses; see
#     FLUTTER_VERSION below.
#  2. Generated sources are gitignored (`*.g.dart`, `app_localizations*`), so a
#     fresh checkout cannot analyze or test until build_runner and gen-l10n
#     have run. CI regenerates them; so do we.
set -euo pipefail

# Local checkouts have their own toolchain; only provision remote containers.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Keep in step with `flutter-version:` in .github/workflows/flutter.yml.
# flutter/test/version_parity_test.dart pins the app version to
# app/build.gradle; this is the toolchain equivalent, by hand.
FLUTTER_VERSION="3.44.8"
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
FLUTTER_APP="$PROJECT_DIR/flutter"

export FLUTTER_SUPPRESS_ANALYTICS=true
export PUB_CACHE="${PUB_CACHE:-$HOME/.pub-cache}"

is_pinned() {
  [ -x "$1/bin/flutter" ] &&
    "$1/bin/flutter" --version 2>/dev/null | grep -q "Flutter $FLUTTER_VERSION"
}

# Reuse an SDK the image already carries before spending a clone on one. Some
# containers ship /opt/flutter; a previous session in this container may have
# left one elsewhere. Disk is a fixed per-session allowance, so a second full
# SDK is worth avoiding.
SDK_DIR=""
for candidate in "${FLUTTER_SDK_DIR:-}" /opt/flutter "$HOME/flutter-sdk" /home/user/flutter_sdk; do
  [ -n "$candidate" ] || continue
  if is_pinned "$candidate"; then
    SDK_DIR="$candidate"
    echo "session-start: reusing pinned Flutter $FLUTTER_VERSION at $SDK_DIR"
    break
  fi
done

if [ -z "$SDK_DIR" ]; then
  SDK_DIR="${FLUTTER_SDK_DIR:-/opt/flutter}"
  if [ -d "$SDK_DIR/.git" ]; then
    # Wrong version in the right place: move the existing checkout to the tag
    # rather than re-downloading the whole SDK.
    echo "session-start: retargeting $SDK_DIR to $FLUTTER_VERSION"
    git config --global --add safe.directory "$SDK_DIR" || true
    git -C "$SDK_DIR" fetch --depth 1 origin \
      "refs/tags/$FLUTTER_VERSION:refs/tags/$FLUTTER_VERSION" 2>/dev/null || true
    git -C "$SDK_DIR" checkout -q "$FLUTTER_VERSION"
  else
    echo "session-start: installing Flutter $FLUTTER_VERSION into $SDK_DIR"
    rm -rf "$SDK_DIR"
    git clone --depth 1 --branch "$FLUTTER_VERSION" \
      https://github.com/flutter/flutter.git "$SDK_DIR"
  fi
fi

git config --global --add safe.directory "$SDK_DIR" || true
export PATH="$SDK_DIR/bin:$PATH"

# Persist the toolchain for every later command in the session.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export PATH=\"$SDK_DIR/bin:\$PATH\""
    echo "export FLUTTER_SUPPRESS_ANALYTICS=true"
  } >> "$CLAUDE_ENV_FILE"
fi

# First invocation downloads the bundled Dart SDK; do it here, not mid-task.
flutter --version

cd "$FLUTTER_APP"
flutter pub get

# Mirrors the "Generate code" step in flutter.yml.
rm -rf .dart_tool/build
dart run build_runner build --delete-conflicting-outputs
flutter gen-l10n

echo "session-start: flutter/ ready — format, analyze and test now match CI"
