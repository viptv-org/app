#!/usr/bin/env bash
# Fails when a pull request moves native/core to a commit that is not newer than the pin it started from.
# The base is the merge base with the target branch, not its tip: a branch that has not merged the
# latest edge is behind by design and must not fail. BASE_SHA, HEAD_SHA and BASE_REF describe the PR.
set -euo pipefail

base_sha=${BASE_SHA:?BASE_SHA is the base commit of the pull request}
head_sha=${HEAD_SHA:?HEAD_SHA is the head commit of the pull request}
base_ref=${BASE_REF:-origin/edge}

base=$(git merge-base "$base_sha" "$head_sha")
base_core=$(git rev-parse "$base:native/core")
head_core=$(git rev-parse "$head_sha:native/core")
echo "merge base $base pins native/core at $base_core"
echo "head $head_sha pins native/core at $head_core"

if [ "$base_core" = "$head_core" ]; then
    echo "native/core is unchanged"
    exit 0
fi

url=$(git config -f .gitmodules --get submodule.native/core.url)
[ -n "$url" ] || { echo "no submodule.native/core.url in .gitmodules" >&2; exit 1; }
core=$(mktemp -d)
trap 'rm -rf "$core"' EXIT
git clone --quiet --filter=blob:none "$url" "$core/repo"
git -C "$core/repo" fetch --quiet origin || true

for sha in "$base_core" "$head_core"; do
    if ! git -C "$core/repo" cat-file -e "$sha^{commit}" 2>/dev/null; then
        git -C "$core/repo" fetch --quiet origin "$sha" 2>/dev/null ||
            { echo "core commit $sha is not reachable from $url" >&2; echo "$base_ref's pull request moved native/core to a commit the submodule does not contain" >&2; exit 1; }
    fi
done

if git -C "$core/repo" merge-base --is-ancestor "$base_core" "$head_core"; then
    echo "native/core moves forward: $base_core -> $head_core"
    exit 0
fi

if [ "$(git -C "$core/repo" rev-list --count "$head_core..$base_core")" -gt 0 ] 2>/dev/null; then
    echo "native/core moves backwards: $base_core -> $head_core" >&2
else
    echo "native/core moves sideways: $base_core -> $head_core" >&2
fi
echo "A pull request may only bump native/core to a commit that contains its starting pin ($base_core)." >&2
echo "Restore it with: git checkout $base_ref -- native/core   (or git submodule update --init native/core)" >&2
exit 1