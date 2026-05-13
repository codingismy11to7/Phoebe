#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

docker run --rm \
  -v "$root:/work" \
  -w /work \
  mcr.microsoft.com/playwright:v1.60.0-jammy \
  bash -lc '
    set -euo pipefail
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk-headless curl > /dev/null
    curl -fsSL https://deb.nodesource.com/setup_22.x | bash - > /dev/null
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nodejs > /dev/null
    npm ci
    npm run web:screenshots:update
  '
