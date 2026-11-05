#!/usr/bin/env bash
set -euo pipefail

cache_dir="${APT_DEB_CACHE_DIR:-$HOME/.cache/apt-debs}"
# Three attempts plus the kill grace must fit inside the step's 12-minute budget.
attempt_seconds="${APT_ATTEMPT_SECONDS:-150}"
mirrors=(
  ''
  'http://archive.ubuntu.com/ubuntu'
  'http://mirrors.edge.kernel.org/ubuntu'
)

APT_PACKAGES=${APT_PACKAGES:?APT_PACKAGES names the packages to install}
export APT_PACKAGES

work_dir=$(mktemp -d)
mkdir -p "$cache_dir/partial"

cleanup() {
  sudo chown -R "$(id -u):$(id -g)" "$cache_dir" || true
  rm -rf "$work_dir"
}
trap cleanup EXIT

write_sources() {
  local mirror=$1 file=$2 codename
  if [[ -f /etc/apt/sources.list.d/ubuntu.sources ]]; then
    sed -E "s#^URIs:.*#URIs: ${mirror}#" /etc/apt/sources.list.d/ubuntu.sources > "$file"
    return
  fi
  codename=$(. /etc/os-release && printf '%s' "${VERSION_CODENAME:-noble}")
  {
    printf 'deb %s %s main restricted universe multiverse\n' "$mirror" "$codename"
    printf 'deb %s %s-updates main restricted universe multiverse\n' "$mirror" "$codename"
    printf 'deb %s %s-security main restricted universe multiverse\n' "$mirror" "$codename"
  } > "$file"
}

for index in "${!mirrors[@]}"; do
  mirror=${mirrors[index]}
  label=${mirror:-"the image's default mirror"}
  options=(
    -o Acquire::Retries=2
    -o Acquire::http::Timeout=30
    -o Acquire::https::Timeout=30
  )
  if [[ -n $mirror ]]; then
    write_sources "$mirror" "$work_dir/ubuntu.sources"
    options+=(-o "Dir::Etc::sourcelist=$work_dir/ubuntu.sources" -o "Dir::Etc::sourceparts=-")
  fi
  echo "apt attempt $((index + 1)) of ${#mirrors[@]} via ${label}"
  started=$SECONDS
  if timeout --kill-after=15 "$attempt_seconds" bash -c '
    set -euo pipefail
    cache_dir=$1
    shift
    read -r -a packages <<< "$APT_PACKAGES"
    sudo apt-get "$@" update
    sudo apt-get "$@" -o "Dir::Cache::archives=${cache_dir}" install -y --no-install-recommends "${packages[@]}"
  ' _ "$cache_dir" "${options[@]}"; then
    echo "apt packages installed via ${label} in $((SECONDS - started))s"
    exit 0
  fi
  echo "apt attempt via ${label} failed after $((SECONDS - started))s"
done

echo "no apt mirror served the packages" >&2
exit 1
