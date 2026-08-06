#!/usr/bin/env bash
#
# myPlanet version helper. The two fields in app/build.gradle are kept in
# lockstep -- versionCode = major*10000 + minor*100 + patch -- with each block
# 0..99, so a patch rollover carries into the minor block:
#
#   0.62.97 / 6297  ->  0.62.98 / 6298
#   0.62.99 / 6299  ->  0.63.0  / 6300
#
# That encoding makes the bumped code always the old code plus one, which the
# script asserts to catch a hand-edited file where the two have drifted apart.
#
# Usage:
#   version.sh read  <gradle-file>          print code= and name= as they are
#   version.sh next  <gradle-file>          print code= and name= for the next
#   version.sh apply <gradle-file> <code> <name>    rewrite them in place
#
set -euo pipefail

die() { echo "version.sh: $*" >&2; exit 1; }

read_version() {
    local file=$1
    [ -f "$file" ] || die "no such file: $file"

    cur_code=$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$file" | head -1)
    cur_name=$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$file" | head -1)

    [ -n "$cur_code" ] || die "could not find versionCode in $file"
    [ -n "$cur_name" ] || die "could not find versionName in $file"
}

next_version() {
    local file=$1
    read_version "$file"

    # minor and patch are the carry blocks and stay at two digits; major is
    # left wider so the script does not become the thing that breaks at 1.0.0.
    [[ "$cur_name" =~ ^([0-9]{1,3})\.([0-9]{1,2})\.([0-9]{1,2})$ ]] \
        || die "versionName '$cur_name' is not <major>.<minor>.<patch> with 1-2 digits in the minor and patch blocks"

    local major=$((10#${BASH_REMATCH[1]}))
    local minor=$((10#${BASH_REMATCH[2]}))
    local patch=$((10#${BASH_REMATCH[3]}))

    patch=$((patch + 1))
    if [ "$patch" -gt 99 ]; then
        patch=0
        minor=$((minor + 1))
    fi
    if [ "$minor" -gt 99 ]; then
        minor=0
        major=$((major + 1))
    fi

    new_name="${major}.${minor}.${patch}"
    new_code=$((major * 10000 + minor * 100 + patch))

    local expected=$((10#$cur_code + 1))
    [ "$new_code" -eq "$expected" ] || die \
        "versionCode $cur_code and versionName $cur_name disagree: bumping the name gives code $new_code but the file implies $expected. Fix app/build.gradle by hand."
}

apply_version() {
    local file=$1 code=$2 name=$3
    [ -f "$file" ] || die "no such file: $file"

    sed -i -E \
        -e "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\1${code}/" \
        -e "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*\")[^\"]+\"/\1${name}\"/" \
        "$file"

    read_version "$file"
    [ "$cur_code" = "$code" ] || die "failed to write versionCode $code into $file"
    [ "$cur_name" = "$name" ] || die "failed to write versionName $name into $file"
}

case "${1:-}" in
    read)
        read_version "${2:?gradle file required}"
        echo "code=$cur_code"
        echo "name=$cur_name"
        ;;
    next)
        next_version "${2:?gradle file required}"
        echo "code=$new_code"
        echo "name=$new_name"
        ;;
    apply)
        apply_version "${2:?gradle file required}" "${3:?code required}" "${4:?name required}"
        ;;
    *)
        die "usage: version.sh {read|next|apply} <gradle-file> [code] [name]"
        ;;
esac
