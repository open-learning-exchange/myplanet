#!/usr/bin/env bats
#
# Tests for the size labeller. The bug (#16344) was that read_files() pulled the
# full unified patch for every changed file via the REST pulls/files endpoint and
# then threw almost all of it away. These tests pin the contract that:
#   * read_files() reads filenames + additions + deletions only -- no per-file
#     patch column (the patch is never parsed from the listing);
#   * the version-file diff comes from a targeted two-blob fetch (gradle_patch),
#     not from a patch the listing happened to ship;
#   * the discount still works for a real version bump and is skipped for
#     non-version changes.

setup() {
    export REPO=ole/mock
    export PR=42
    export GRADLE_FILE=app/build.gradle
    export EXCLUDE_PATHS='app/src/main/res/values-*/strings.xml'
    export DRY_RUN=true
    export FILES_JSON="$BATS_TMPDIR/files-$BATS_TEST_NAME.json"
    # isolate per-test blob registrations and resolved SHAs (stub_blob/gradle_blob)
    unset GRADLE_BLOB_h GRADLE_BLOB_b GRADLE_HEAD_SHA GRADLE_BASE_SHA
    load test_helper
}

teardown() {
    unset GRADLE_BLOB_h GRADLE_BLOB_b GRADLE_HEAD_SHA GRADLE_BASE_SHA
}

# Feed a files-listing JSON (path/additions/deletions, no patch) for the offline
# read_files path.
feed() { printf '%s' "$1" > "$FILES_JSON"; }

@test "read_files emits only filename/additions/deletions (no patch column)" {
    feed '[{"path":"a.kt","additions":3,"deletions":1},{"path":"b.kt","additions":2,"deletions":0}]'
    run bash -c "set -euo pipefail; $(label_src); read_files"
    [ "$status" -eq 0 ]
    [ "$(printf '%s\n' "$output" | awk -F'\t' '{print NF}' | sort -u)" = "3" ]
    [[ "$output" == *"a.kt	3	1"* ]]
    [[ "$output" == *"b.kt	2	0"* ]]
}

@test "read_files tolerates null additions/deletions" {
    feed '[{"path":"z","additions":null,"deletions":null}]'
    run bash -c "set -euo pipefail; $(label_src); read_files"
    [ "$status" -eq 0 ]
    [[ "$output" == "z	0	0" ]]
}

@test "gradle_patch reconstructs the version-file diff from two blobs" {
    install_gh_stub
    export GRADLE_HEAD_SHA=h
    export GRADLE_BASE_SHA=b
    stub_blob h $'versionCode = 6759\nversionName = "0.67.59"\n'
    stub_blob b $'versionCode = 6758\nversionName = "0.67.58"\n'
    run bash -c "set -euo pipefail; $(label_src); gradle_patch"
    [ "$status" -eq 0 ]
    [[ "$output" == *"+versionCode = 6759"* ]]
    [[ "$output" == *"-versionCode = 6758"* ]]
    [[ "$output" == *"+versionName = \"0.67.59\""* ]]
    [[ "$output" == *"-versionName = \"0.67.58\""* ]]
}

@test "gradle_patch is empty when the version file is unchanged" {
    install_gh_stub
    export GRADLE_HEAD_SHA=h
    export GRADLE_BASE_SHA=b
    stub_blob h $'versionCode = 6759\n'
    stub_blob b $'versionCode = 6759\n'
    run bash -c "set -euo pipefail; $(label_src); gradle_patch"
    [ "$status" -eq 0 ]
    [ -z "$output" ]
}

@test "version bump is discounted using the reconstructed gradle patch" {
    install_gh_stub
    # the gradle file's raw 2/2 plus a real code change so the PR isn't "nothing"
    feed '[{"path":"app/build.gradle","additions":2,"deletions":2},{"path":"a.kt","additions":10,"deletions":0}]'
    export GRADLE_HEAD_SHA=h
    export GRADLE_BASE_SHA=b
    stub_blob h $'versionCode = 6759\nversionName = "0.67.59"\n'
    stub_blob b $'versionCode = 6758\nversionName = "0.67.58"\n'
    run bash -c "set -euo pipefail; $(label_src); main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"discounting the version bump"* ]]
    # gradle's 2/2 is discounted to 0/0, so only the 10 additions remain
    [[ "$output" == *"+10/-0"* ]]
}

@test "a non-version gradle change is NOT discounted" {
    install_gh_stub
    feed '[{"path":"app/build.gradle","additions":5,"deletions":0}]'
    export GRADLE_HEAD_SHA=h
    export GRADLE_BASE_SHA=b
    stub_blob h $'applicationId "org.ole.new"\nversionCode = 6759\n'
    stub_blob b $'applicationId "org.ole.old"\nversionCode = 6759\n'
    run bash -c "set -euo pipefail; $(label_src); main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"+5/-0"* ]]
    [[ "$output" != *"discounting the version bump"* ]]
}

@test "an unreadable version-file diff warns instead of silently dropping the discount" {
    install_gh_stub
    # listing reports the version file changed, but both blobs read back empty
    # (as they would under a missing contents:read scope)
    feed '[{"path":"app/build.gradle","additions":2,"deletions":2}]'
    export GRADLE_HEAD_SHA=h
    export GRADLE_BASE_SHA=b
    # no stub_blob -> gradle_blob returns "" for both shas
    run bash -c "set -euo pipefail; $(label_src); main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING: could not read app/build.gradle diff"* ]]
    [[ "$output" == *"check contents:read permission"* ]]
    # the 2/2 is NOT discounted
    [[ "$output" == *"+2/-2"* ]]
}

@test "excluded translation strings are skipped" {
    feed '[{"path":"app/src/main/res/values-fr/strings.xml","additions":1,"deletions":0},{"path":"a.kt","additions":1,"deletions":0}]'
    run bash -c "set -euo pipefail; $(label_src); main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"skipping app/src/main/res/values-fr/strings.xml"* ]]
    [[ "$output" == *"+1/-0"* ]]
}

@test "only-removals PR gets the less label" {
    feed '[{"path":"a.kt","additions":0,"deletions":5}]'
    run bash -c "set -euo pipefail; $(label_src); main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"+0/-5"* ]]
    [[ "$output" == *"small + less"* ]]
}

@test "read_files does not consume a patch even if the listing ships one" {
    feed '[{"path":"a.kt","additions":1,"deletions":0,"patch":"@@ -1 +1 @@\n-OLD\n+NEW"}]'
    run bash -c "set -euo pipefail; $(label_src); read_files"
    [ "$status" -eq 0 ]
    [[ "$output" != *"OLD"* ]]
    [[ "$output" != *"NEW"* ]]
    [[ "$output" == "a.kt	1	0" ]]
}
