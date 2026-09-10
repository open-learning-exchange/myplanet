#!/usr/bin/env bash
#
# Shared scaffolding for the labels.sh bats tests. Loaded via `load test_helper`
# from test_labels.bats. Lets the tests exercise the real bash logic in
# labels.sh without network or `gh`: every `gh` call the script makes is
# overridden with a stub, and the version-file blobs are supplied by the test.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LABELS_SH="$SCRIPT_DIR/labels.sh"

# Emit labels.sh with the shebang and trailing top-level `main` invocation
# stripped, so a test can substitute the result into `bash -c "$(label_src);
# <fn>"` and source every function (read_files, gradle_patch, version_lines,
# main) without the script running. We drop the first line (shebang) and cut
# from the last top-level `main` line on.
label_src() {
    awk 'NR==1 && /^#!/ { next }
         /^main[[:space:]]*$/ { exit }
         { print }' "$LABELS_SH"
}

# Register a fake Blob body for a stub sha. gradle_blob() looks up the env var
# GRADLE_BLOB_<sha>; this helper stores it (base64-wrapped to keep newlines and
# quotes intact across the env boundary).
stub_blob() {
    local sha="$1" body="$2" b64
    b64=$(printf '%s' "$body" | base64 -w0)
    printf -v "GRADLE_BLOB_${sha}" '%s' "$b64"
    export "GRADLE_BLOB_${sha}"
}

# Decode a registered blob back to text.
_read_blob() {
    local v="GRADLE_BLOB_$1"
    [ -n "${!v:-}" ] && printf '%s' "${!v}" | base64 -d || printf ''
}

# Build the `gh` stub on PATH. Two flavours of `gh` call labels.sh makes:
#   * `gh api repos/.../pulls/N`   -> emits `GRADLE_HEAD_SHA=.. GRADLE_BASE_SHA=..`
#     (matches the `@sh` template main() evals); driven by GRADLE_HEAD_SHA/GRADLE_BASE_SHA.
#   * `gh api graphql ...`         -> a graphql-files listing OR a blob fetch.
#     The files listing returns `[{"path","additions","deletions"}, ...]` shaped JSON
#     (tests seed it via FILES_JSON for the offline path); the blob fetch returns
#     the registered blob for the `<sha>:<path>` expression.
#   * `gh pr view`/`gh pr edit`    -> no-ops / echo.
install_gh_stub() {
    local dir="$BATS_TMPDIR/gh-stub-$BATS_TEST_NAME"
    mkdir -p "$dir"
    cat > "$dir/gh" <<'STUB'
#!/usr/bin/env bash
# minimal `gh` stub for labels.sh tests
set -euo pipefail
jq_arg=""
# collect --jq value and -F expr= value by scanning args
prev=""
expr=""
for a in "$@"; do
    if [ "$prev" = "--jq" ]; then jq_arg="$a"; prev=""; continue; fi
    if [ "$prev" = "-F" ]; then
        case "$a" in expr=*) expr="${a#expr=}" ;; esac
        prev=""; continue
    fi
    case "$a" in
        --jq) prev="--jq" ;;
        -F) prev="-F" ;;
    esac
done

case "$1:$2" in
  api:graphql)
    if [ -n "$expr" ]; then
        sha="${expr%%:*}"
        txt="$(_read_blob "$sha")"
        payload="{\"data\":{\"repository\":{\"object\":{\"text\":$(printf '%s' "$txt" | jq -Rs .)}}}}"
    else
        payload="$(cat "$FILES_JSON")"
    fi
    if [ -n "$jq_arg" ]; then
        printf '%s' "$payload" | jq -r "$jq_arg"
    else
        printf '%s' "$payload"
    fi
    ;;
  api:repos/*)
    # pulls/N: emit the sha template main() evals
    printf 'GRADLE_HEAD_SHA=%s GRADLE_BASE_SHA=%s\n' "${GRADLE_HEAD_SHA:-}" "${GRADLE_BASE_SHA:-}"
    ;;
  pr:*)
    case "$3" in
      view) printf '[]\n' ;;
      edit) ;;
    esac
    ;;
esac
STUB
    chmod +x "$dir/gh"
    export PATH="$dir:$PATH"
    export -f _read_blob
}
