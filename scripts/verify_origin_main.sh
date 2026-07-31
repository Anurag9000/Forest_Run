#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "${ROOT}"

if ! git remote get-url origin >/dev/null 2>&1; then
  echo "Release preparation requires the canonical Git remote named origin." >&2
  exit 1
fi

git fetch --quiet --no-tags origin \
  +refs/heads/main:refs/remotes/origin/main

head_sha="$(git rev-parse HEAD)"
local_main_sha="$(git rev-parse refs/heads/main)"
origin_main_sha="$(git rev-parse refs/remotes/origin/main)"

if [[ "${head_sha}" != "${local_main_sha}" ]]; then
  echo "HEAD is not the exact local main tip." >&2
  echo "HEAD=${head_sha}" >&2
  echo "refs/heads/main=${local_main_sha}" >&2
  exit 1
fi

if [[ "${head_sha}" != "${origin_main_sha}" ]]; then
  echo "Local main is not the canonical origin/main tip." >&2
  echo "local=${head_sha}" >&2
  echo "origin/main=${origin_main_sha}" >&2
  exit 1
fi

printf '%s\n' "${origin_main_sha}"
