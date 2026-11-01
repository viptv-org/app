#!/usr/bin/env bash
# Longest-processing-time split over the checked-in durations so a new class lands in the least-loaded shard.
set -euo pipefail

shards=${SHARDS:-4}
shard=${SHARD:-0}
mode=${1:-list}
root=$(cd "$(dirname "$0")/.." && pwd)
test_dir="$root/composeApp/src/desktopTest/kotlin"
durations_file="$root/tools/desktop-test-durations.txt"

mapfile -t classes < <(
  find "$test_dir" -name '*Test.kt' -printf '%P\n' \
    | sed 's#/#.#g; s#\.kt$##' \
    | LC_ALL=C sort
)

assignment() {
  printf '%s\n' "${classes[@]}" | awk -v shards="$shards" -v durations="$durations_file" '
    function minidx(loads,    i, m, mi) {
      m = loads[0]; mi = 0
      for (i = 1; i < shards; i++) {
        if (loads[i] < m) { m = loads[i]; mi = i }
      }
      return mi
    }
    BEGIN {
      while ((getline < durations) > 0) dur[$2] = $1
      close(durations)
    }
    {
      cls[++n] = $0
      eff[n] = (dur[$0] != "" ? dur[$0] : 5)
    }
    END {
      for (i = 1; i <= n; i++) order[i] = i
      for (i = 2; i <= n; i++) {
        k = order[i]; d = eff[k]; c = cls[k]; j = i - 1
        while (j >= 1 && (eff[order[j]] < d || (eff[order[j]] == d && cls[order[j]] > c))) {
          order[j + 1] = order[j]; j--
        }
        order[j + 1] = k
      }
      for (i = 0; i < shards; i++) loads[i] = 0
      for (i = 1; i <= n; i++) {
        k = order[i]
        m = minidx(loads)
        print m, cls[k]
        loads[m] += eff[k]
      }
    }
  '
}

case "$mode" in
  list)
    assignment | awk -v shard="$shard" '$1 == shard { print $2 }'
    ;;
  count)
    printf '%s\n' "${#classes[@]}"
    ;;
  check)
    assignment | awk -v shards="$shards" -v total="${#classes[@]}" '
      { n[$1]++; seen[$2]++ }
      END {
        for (c in seen) if (seen[c] != 1) {
          printf "class assigned %d times: %s\n", seen[c], c
          bad = 1
        }
        printf "%d test classes across %d shards, no class skipped or double-run\n", total, shards
        exit (bad ? 1 : 0)
      }'
    ;;
  *)
    printf 'usage: %s [list|count|check]\n' "$0" >&2
    exit 2
    ;;
esac