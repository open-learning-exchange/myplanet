#!/usr/bin/env bash
#
# Build the squash commit body for a PR: one Co-authored-by line per real
# person who worked on it and is a collaborator here, then the repo owner on
# anything they did not author themselves. The PR description is discarded.
#
# Dropped: the PR author (already the commit author), non-collaborators, and
# coding agents -- an agent either commits as a Bot account or leaves a
# trailer whose address is not a GitHub account, so only trailers that tie
# back to a real account survive and no denylist is needed.
#
# Usage: REPO=owner/name PR=123 coauthors.sh
# Prints the body on stdout; empty output is a legitimate result.
#
set -euo pipefail

REPO="${REPO:?}"
PR="${PR:?}"
# Appended to every PR this person did not author themselves.
OWNER_LOGIN="${OWNER_LOGIN:-dogi}"

lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

noreply_for() { printf '%s <%s@users.noreply.github.com>' "$1" "$1"; }

# ------------------------------------------------------------- gather

# Needs push access; failing here beats crediting nobody.
collaborators=$(
    gh api "repos/$REPO/collaborators?per_page=100" --paginate \
        --jq '.[] | select(.type == "User") | .login' | tr '[:upper:]' '[:lower:]' | sort -u
)

pr_json=$(gh pr view "$PR" --repo "$REPO" --json author,body)
author_login=$(jq -r '.author.login // ""' <<<"$pr_json")
pr_body=$(jq -r '.body // ""' <<<"$pr_json")

commits=$(gh api "repos/$REPO/pulls/$PR/commits?per_page=100" --paginate)

candidates=""

# Commit authors GitHub resolved to an account; type "Bot" is skipped.
while IFS= read -r login; do
    if [ -n "$login" ]; then
        candidates+="$login"$'\n'
    fi
done < <(jq -r '.[] | select(.author != null) | select(.author.type == "User") | .author.login' <<<"$commits")

# Co-authored-by trailers, from both the commit messages and the PR body.
trailers=$(
    { jq -r '.[].commit.message' <<<"$commits"; printf '%s\n' "$pr_body"; } \
    | grep -iE '^[[:space:]]*co-authored-by:[[:space:]]*' || true
)

while IFS= read -r line; do
    if [ -z "$line" ]; then
        continue
    fi
    email=$(sed -nE 's/.*<([^>]+)>.*/\1/p' <<<"$line")
    case "$email" in
        *@users.noreply.github.com)
            login=${email%@users.noreply.github.com}
            login=${login#*+}   # drop the numeric id prefix, if any
            candidates+="$login"$'\n'
            ;;
        *)
            # Not a GitHub address -- cannot be tied to an account. This is
            # the branch that removes the coding agents.
            ;;
    esac
done <<<"$trailers"

# ------------------------------------------------------------- filter

author_l=$(lower "$author_login")
owner_l=$(lower "$OWNER_LOGIN")

body=""
seen=""

while IFS= read -r login; do
    if [ -z "$login" ]; then
        continue
    fi
    l=$(lower "$login")

    case "$l" in
        *'[bot]') continue ;;
    esac
    if [ "$l" = "$author_l" ]; then
        continue                       # already the commit author
    fi
    if [ "$l" = "$owner_l" ]; then
        continue                       # appended at the end instead
    fi
    if printf '%s' "$seen" | grep -qxF "$l"; then
        continue
    fi
    if ! printf '%s' "$collaborators" | grep -qxF "$l"; then
        continue
    fi

    seen+="$l"$'\n'
    body+="Co-authored-by: $(noreply_for "$login")"$'\n'
done <<<"$candidates"

if [ -n "$author_l" ] && [ "$author_l" != "$owner_l" ]; then
    body+="Co-authored-by: $(noreply_for "$OWNER_LOGIN")"$'\n'
fi

printf '%s' "$body"
