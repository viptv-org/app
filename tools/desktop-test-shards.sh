#!/usr/bin/env bash
# Deterministically assign every desktop test class to one CI shard so the jobs
# run disjoint subsets with Gradle's --tests filter. Assignment is a hash of the
# fully qualified class name, so a new test class lands in a shard automatically.
set -euo pipefail

shards=${SHARDS:-4}
shard=${SHARD:-0}
mode=${1:-list}
root=$(cd "$(dirname "$0")/.." && pwd)
test_dir="$root/composeApp/src/desktopTest/kotlin"

mapfile -t classes < <(
  find "$test_dir" -name '*Test.kt' -printf '%P\n' \
    | sed 's#/#.#g; s#\.kt$##' \
    | LC_ALL=C sort
)

shard_of() {
  local class="$1"
  local hash
  hash=$(printf '%s' "$class" | sha256sum | cut -d' ' -f1)
  printf '%d' "$((16#${hash:0:8} % shards))"
}

case "$mode" in
  list)
    for class in "${classes[@]}"; do
      if (( $(shard_of "$class") == shard )); then
        printf '%s\n' "$class"
      fi
    done
    ;;
  count)
    printf '%s\n' "${#classes[@]}"
    ;;
  sizes)
    n=()
    for class in "${classes[@]}"; do
      s=$(shard_of "$class")
      (( n[s]++ )) || true
    done
    for (( i = 0; i < shards; i++ )); do
      printf 'shard %d: %d\n' "$i" "${n[$i]:-0}"
    done
    printf 'total: %d\n' "${#classes[@]}"
    ;;
  check)
    n=()
    for class in "${classes[@]}"; do
      s=$(shard_of "$class")
      (( n[s]++ )) || true
    done
    for (( i = 0; i < shards; i++ )); do
      printf 'shard %d runs %d classes\n' "$i" "${n[$i]:-0}"
    done
    printf '%d distinct classes across %d shards\n' "$(printf '%s\n' "${classes[@]}" | sort -u | wc -l)" "$shards"
    (( ${#classes[@]} == $(printf '%s\n' "${classes[@]}" | sort -u | wc -l) ))
    ;;
  *)
    printf 'usage: %s [list|count|sizes|check]\n' "$0" >&2
    exit 2
    ;;
esac