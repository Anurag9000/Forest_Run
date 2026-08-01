#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
readonly EXPECTED_ORIGIN="${2:-https://github.com/Anurag9000/Forest_Run.git}"
cd "${ROOT}"

normalize_origin() {
  local raw="${1%/}"
  raw="${raw%.git}"
  case "${raw}" in
    git@github.com:*)
      printf 'github.com/%s\n' "${raw#git@github.com:}"
      ;;
    ssh://git@github.com/*)
      printf 'github.com/%s\n' "${raw#ssh://git@github.com/}"
      ;;
    https://github.com/*)
      printf 'github.com/%s\n' "${raw#https://github.com/}"
      ;;
    *)
      printf '%s\n' "${raw}"
      ;;
  esac
}

if ! origin_url="$(git remote get-url origin 2>/dev/null)"; then
  echo "Release preparation requires the canonical Git remote named origin." >&2
  exit 1
fi

normalized_origin="$(normalize_origin "${origin_url}")"
normalized_expected="$(normalize_origin "${EXPECTED_ORIGIN}")"
if [[ "${normalized_origin}" != "${normalized_expected}" ]]; then
  echo "Remote origin does not identify the canonical Forest Run repository." >&2
  echo "origin=${origin_url}" >&2
  echo "expected=${EXPECTED_ORIGIN}" >&2
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
