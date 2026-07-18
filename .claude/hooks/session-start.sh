#!/usr/bin/env bash
# SessionStart hook: provision the Android SDK and warm the Gradle cache so that
# builds, unit tests, and lint work in Claude Code on the web sessions.
#
# Idempotent and non-interactive. Safe to run repeatedly: every step is guarded
# so a warm container reuses what the previous run installed.
set -euo pipefail

# Only provision in the remote (web) environment; a local machine already has its
# own SDK/JDK and should not be mutated by this hook.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

log() { printf '[session-start] %s\n' "$*" >&2; }

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"

# Versions are derived from the build config, not hard-coded guesses:
#   compileSdk = 37  ->  platforms;android-37.0 + build-tools;37.0.0
# NOTE: sdkmanager only ships the minor-versioned package "platforms;android-37.0"
# (there is no plain "android-37"); it installs into a dir named "android-37.0".
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
ANDROID_PLATFORM="platforms;android-37.0"
BUILD_TOOLS="build-tools;37.0.0"

# ---------------------------------------------------------------------------
# 1. JDK guard — Gradle 9.x needs JDK 17+. The web image ships JDK 21, which
#    runs Gradle fine and still targets Java-17 bytecode, so accept >= 17.
# ---------------------------------------------------------------------------
JAVA_MAJOR="$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)"
if [ -z "${JAVA_MAJOR:-}" ] || [ "$JAVA_MAJOR" -lt 17 ]; then
  log "ERROR: JDK 17+ required, found '${JAVA_MAJOR:-none}'"
  exit 1
fi
log "JDK $JAVA_MAJOR OK"

# ---------------------------------------------------------------------------
# 2. Android command-line tools (only if missing).
# ---------------------------------------------------------------------------
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  log "Installing Android command-line tools into $SDK_ROOT"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  tmp_zip="$(mktemp --suffix=.zip)"
  curl -fsSL -o "$tmp_zip" \
    "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
  rm -rf "$SDK_ROOT/cmdline-tools/latest" "$SDK_ROOT/cmdline-tools/cmdline-tools"
  unzip -q "$tmp_zip" -d "$SDK_ROOT/cmdline-tools"
  mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -f "$tmp_zip"
else
  log "Android command-line tools already present"
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

# ---------------------------------------------------------------------------
# 3. SDK packages (sdkmanager is idempotent — it no-ops when already installed).
# ---------------------------------------------------------------------------
log "Accepting licenses and installing SDK packages"
# `yes | sdkmanager` races: once sdkmanager has read enough "y"s (or exits fast
# because everything is already installed) it closes the pipe, so `yes` dies
# with SIGPIPE (141). Under `pipefail` that would abort an otherwise-successful
# warm run, so evaluate sdkmanager's own exit code via PIPESTATUS and treat a
# 141 from `yes` as success.
set +o pipefail
yes | sdkmanager --licenses >/dev/null 2>&1
yes | sdkmanager "platform-tools" "$ANDROID_PLATFORM" "$BUILD_TOOLS" >/dev/null
sdk_rc=${PIPESTATUS[1]}
set -o pipefail
if [ "$sdk_rc" -ne 0 ]; then
  log "ERROR: sdkmanager failed to install packages (exit $sdk_rc)"
  exit 1
fi
# AGP 9.x resolves compileSdk = 37 to this minor-versioned platform natively;
# no android-37 alias is needed (and adding one makes sdkmanager warn about an
# "inconsistent location").

# ---------------------------------------------------------------------------
# 4. Point Gradle at the SDK regardless of env-var persistence.
# ---------------------------------------------------------------------------
if ! grep -qs "^sdk.dir=" "$PROJECT_DIR/local.properties" 2>/dev/null; then
  echo "sdk.dir=$SDK_ROOT" > "$PROJECT_DIR/local.properties"
  log "Wrote local.properties (sdk.dir=$SDK_ROOT)"
fi

# ---------------------------------------------------------------------------
# 5. Persist environment for the agent's shells.
# ---------------------------------------------------------------------------
# The hook can fire more than once per session (startup/resume/clear/compact)
# against the same env file, so append each line only if it is not already there.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  add_env() {
    grep -qsF -- "$1" "$CLAUDE_ENV_FILE" 2>/dev/null || echo "$1" >> "$CLAUDE_ENV_FILE"
  }
  add_env "export ANDROID_SDK_ROOT=$SDK_ROOT"
  add_env "export ANDROID_HOME=$SDK_ROOT"
  add_env "export PATH=$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:\$PATH"
fi

# ---------------------------------------------------------------------------
# 6. Warm the Gradle cache while the network is available, so later builds are
#    fast and resilient. --no-daemon keeps the container-cache footprint clean.
#
#    The wrapper distribution (gradle-9.6.1) is served as a GitHub release asset.
#    Some egress policies allow package registries (Maven Central, dl.google.com)
#    but not github.com, which makes the wrapper download fail with an opaque
#    HTTP 403. Preflight the resolved distribution host and, if it is blocked,
#    emit an actionable message and skip the warm-up instead of hard-failing the
#    session — the SDK is still provisioned and usable.
# ---------------------------------------------------------------------------
DIST_URL="$(sed -n 's/^distributionUrl=//p' \
  "$PROJECT_DIR/gradle/wrapper/gradle-wrapper.properties" | sed 's/\\//g')"
# Fetch just the first byte (-r 0-0) so the reachability probe stays cheap
# instead of pulling the whole ~150 MB distribution. A reachable host answers
# 206 (range honored) or 200 (range ignored); a policy block answers 403.
DIST_CODE="$(curl -sSL -r 0-0 -o /dev/null -w '%{http_code}' --max-time 60 "$DIST_URL" 2>/dev/null || echo 000)"

if [ "$DIST_CODE" = "200" ] || [ "$DIST_CODE" = "206" ]; then
  log "Warming Gradle cache (this is the slow part on a cold container)"
  ( cd "$PROJECT_DIR" && ./gradlew --no-daemon --quiet help >/dev/null )
  log "Environment ready: SDK at $SDK_ROOT, JDK $JAVA_MAJOR, Gradle cache warmed"
else
  log "WARNING: cannot reach the Gradle distribution ($DIST_URL -> HTTP $DIST_CODE)."
  log "         The Android SDK is installed, but Gradle builds/tests will fail"
  log "         until this environment's network policy permits the Gradle"
  log "         distribution download (it redirects to a github.com release"
  log "         asset). Fix: use a network policy with GitHub/full access, or"
  log "         pre-provision Gradle 9.6.1 at environment-setup time."
  log "Environment partially ready: SDK at $SDK_ROOT, JDK $JAVA_MAJOR (Gradle unreachable)"
fi
