#!/usr/bin/env bash
set -euo pipefail

cache_dir="${APT_DEB_CACHE_DIR:-$HOME/.cache/apt-debs}"
mkdir -p "$cache_dir"

for attempt in 1 2; do
  echo "apt install attempt ${attempt} of 2"
  if timeout 150 sudo apt-get -o Acquire::Retries=3 update \
     && timeout 150 sudo apt-get -o Acquire::Retries=3 \
        -o "Dir::Cache::archives=${cache_dir}" install -y "$@"; then
    exit 0
  fi
done
exit 1