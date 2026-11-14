#!/usr/bin/env bash
# Copies the generated Kotlin API models from a contracts checkout into
# api/kotlin/, at the revision pinned in api/CONTRACTS_VERSION.
#
#   scripts/sync-api-models.sh            copy the pinned revision into api/kotlin/
#   scripts/sync-api-models.sh --check    exit 1 if api/kotlin/ differs from the pin
#   scripts/sync-api-models.sh --pin REF  pin REF (tag or commit), then copy
#
# The files are read with `git archive` at the pinned commit, so uncommitted
# changes or the branch checked out in the contracts working tree do not matter.
# CONTRACTS_DIR points to the contracts checkout (default: ../contracts,
# relative to this repo's root).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
contracts_dir="${CONTRACTS_DIR:-$repo_root/../contracts}"
pin_file="$repo_root/api/CONTRACTS_VERSION"
dest="$repo_root/api/kotlin"
# Path of the generated models inside the contracts repo.
src_path="kotlin/src/com"

die() {
    echo "sync-api-models: $*" >&2
    exit 2
}

usage() {
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
}

mode=sync
new_ref=""
case "${1:-}" in
    "") ;;
    --check) mode=check ;;
    --pin)
        mode=pin
        new_ref="${2:-}"
        [[ -n "$new_ref" ]] || die "--pin needs a tag or commit"
        ;;
    -h | --help)
        usage
        exit 0
        ;;
    *) die "unknown argument '$1' (see --help)" ;;
esac

git -C "$contracts_dir" rev-parse --git-dir >/dev/null 2>&1 ||
    die "no contracts git checkout at '$contracts_dir' (set CONTRACTS_DIR)"

resolve() {
    git -C "$contracts_dir" rev-parse --verify --quiet "$1^{commit}" ||
        die "'$1' is not a commit in $contracts_dir (fetch it first?)"
}

if [[ "$mode" == pin ]]; then
    commit="$(resolve "$new_ref")"
    # Keep the explanatory header, replace the ref/commit lines.
    header="$(grep '^#' "$pin_file" || true)"
    printf '%s\nref=%s\ncommit=%s\n' "$header" "$new_ref" "$commit" >"$pin_file"
    echo "pinned $new_ref ($commit)"
    mode=sync
fi

ref="$(sed -n 's/^ref=//p' "$pin_file")"
commit="$(sed -n 's/^commit=//p' "$pin_file")"
[[ -n "$ref" && -n "$commit" ]] || die "$pin_file needs ref= and commit= lines"

# A moved tag must not silently change the models: ref and commit have to agree.
resolved="$(resolve "$ref")"
[[ "$resolved" == "$commit" ]] ||
    die "ref '$ref' resolves to $resolved, but $pin_file pins $commit"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
git -C "$contracts_dir" archive --format=tar "$commit" "$src_path" | tar -x -C "$tmp"
pinned="$tmp/$src_path"

if [[ "$mode" == check ]]; then
    if diff -r "$pinned" "$dest/com" >"$tmp/diff" 2>&1; then
        echo "api/kotlin matches contracts $ref ($commit)"
        exit 0
    fi
    echo "api/kotlin differs from contracts $ref ($commit):" >&2
    cat "$tmp/diff" >&2
    echo "run scripts/sync-api-models.sh to restore the pinned models" >&2
    exit 1
fi

rm -rf "$dest"
mkdir -p "$dest"
cp -R "$pinned" "$dest/com"
echo "copied contracts $ref ($commit) into api/kotlin"
