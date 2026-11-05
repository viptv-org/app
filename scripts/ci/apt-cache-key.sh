#!/usr/bin/env bash
set -euo pipefail

packages=${APT_PACKAGES:?APT_PACKAGES names the packages to install}
image=${ImageOS:?ImageOS is set on hosted runners}
version=${ImageVersion:?ImageVersion is set on hosted runners}
key="apt-debs-${image}-${version}-${RUNNER_ARCH:-X64}-${packages// /-}"

printf 'APT_DEB_CACHE_KEY=%s\n' "$key"