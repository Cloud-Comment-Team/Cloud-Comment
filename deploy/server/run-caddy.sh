#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${APP_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
APP_HOME="$(cd "$APP_DIR/.." && pwd)"
APP_ENV_FILE="${APP_ENV_FILE:-$APP_HOME/shared/cloud-comment.env}"

set -a
source "$APP_ENV_FILE"
set +a

exec caddy run --config "$APP_DIR/deploy/caddy/Caddyfile" --adapter caddyfile
