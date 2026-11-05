#!/usr/bin/env bash
set -euo pipefail

cache_dir="${APT_DEB_CACHE_DIR:-$HOME/.cache/apt-debs}"
mkdir -p "$cache_dir"

for attempt in 1 2; do
  echo "apt install attempt ${attempt} of 2"
  if timeout 300 bash -c '
    set -euo pipefail
    cache_dir=$1
    shift
    sudo apt-get -o Acquire::Retries=3 update \
      && sudo apt-get -o Acquire::Retries=3 \
           -o "Dir::Cache::archives=${cache_dir}" install -y --no-install-recommends "$@"
  ' _ "$cache_dir" "$@"; then
    exit 0
  fi
done
exit 1