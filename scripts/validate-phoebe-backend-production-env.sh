#!/usr/bin/env bash
set -euo pipefail

env_file="${1:-.vercel/.env.production.local}"

if [[ -f "${env_file}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${env_file}"
  set +a
elif [[ "${1:-}" != "" ]]; then
  echo "::error::Phoebe backend production env file was not found at ${env_file}"
  exit 1
fi

missing=()
require_non_empty() {
  local name="$1"
  local value="${!name-}"
  if [[ -z "${value:-}" ]]; then
    missing+=("${name}")
  fi
}

is_truthy() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

require_non_empty "TICKETMASTER_API_KEY"
require_non_empty "SEATGEEK_CLIENT_ID"
require_non_empty "GENIUS_ACCESS_TOKEN"

if [[ -z "${ALLOWED_ORIGINS:-}" ]] && ! is_truthy "${BACKEND_ALLOW_ANY_ORIGIN:-}"; then
  missing+=("ALLOWED_ORIGINS")
fi

if (( ${#missing[@]} > 0 )); then
  echo "::error::Missing required Phoebe backend production env values: ${missing[*]}"
  exit 1
fi

echo "Phoebe backend production env includes the values required by release smoke tests."
