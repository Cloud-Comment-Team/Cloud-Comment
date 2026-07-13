#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/home/team13/cloud-comment}"
APP_USER="${APP_USER:-team13}"
PUBLIC_BASE_URL="${CLOUD_COMMENT_PUBLIC_BASE_URL:-https://team13.st.ifbest.org}"
WIDGET_BASE_URL="${CLOUD_COMMENT_WIDGET_BASE_URL:-}"
EDGE_PROXY_CIDRS="${CLOUD_COMMENT_CADDY_TRUSTED_PROXIES:-}"
SITE_ADDRESS="${CLOUD_COMMENT_SITE_ADDRESS:-http://team13.st.ifbest.org}"
WIDGET_SITE_ADDRESS="${CLOUD_COMMENT_WIDGET_SITE_ADDRESS:-http://widget.team13.st.ifbest.org}"

CURRENT_DIR="$APP_DIR/current"
SHARED_DIR="$APP_DIR/shared"
LOG_DIR="$SHARED_DIR/logs"
ENV_FILE="$SHARED_DIR/cloud-comment.env"
SUPERVISOR_CONFIG="$SHARED_DIR/supervisord.conf"

if [ "$(id -u)" -ne 0 ]; then
  echo "bootstrap.sh must be run as root" >&2
  exit 1
fi

if [ -z "$WIDGET_BASE_URL" ] || [ "$WIDGET_BASE_URL" = "$PUBLIC_BASE_URL" ]; then
  echo "CLOUD_COMMENT_WIDGET_BASE_URL must be a dedicated origin" >&2
  exit 1
fi

if [ -z "$EDGE_PROXY_CIDRS" ]; then
  echo "CLOUD_COMMENT_CADDY_TRUSTED_PROXIES must contain the exact edge proxy CIDR list" >&2
  exit 1
fi
for cidr in $EDGE_PROXY_CIDRS; do
  if [[ ! "$cidr" =~ ^[0-9A-Fa-f:.]+/[0-9]{1,3}$ ]]; then
    echo "Invalid trusted edge proxy CIDR: $cidr" >&2
    exit 1
  fi
done

install_packages() {
  local missing=0
  for command_name in java psql caddy supervisord supervisorctl curl rsync; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
      missing=1
    fi
  done

  if [ "$missing" -eq 1 ]; then
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
      postgresql \
      caddy \
      supervisor \
      openjdk-25-jre-headless \
      ca-certificates \
      curl \
      rsync
  fi
}

generate_password() {
  local password
  set +o pipefail
  password="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
  set -o pipefail
  printf '%s' "$password"
}

ensure_directories() {
  mkdir -p "$CURRENT_DIR" "$SHARED_DIR" "$LOG_DIR"
  chown -R "$APP_USER:$APP_USER" "$APP_DIR"
}

ensure_env_file() {
  if [ -f "$ENV_FILE" ]; then
    return
  fi

  local db_password
  local edge_proxy_cidrs_escaped
  db_password="$(generate_password)"
  printf -v edge_proxy_cidrs_escaped '%q' "$EDGE_PROXY_CIDRS"
  cat >"$ENV_FILE" <<EOF
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/cloud_comment
SPRING_DATASOURCE_USERNAME=cloud_comment
SPRING_DATASOURCE_PASSWORD=$db_password

CLOUD_COMMENT_PUBLIC_BASE_URL=$PUBLIC_BASE_URL
CLOUD_COMMENT_WIDGET_SCRIPT_URL=$PUBLIC_BASE_URL/widget/cloud-comment-widget.js
CLOUD_COMMENT_PUBLIC_API_BASE_URL=$PUBLIC_BASE_URL/api
CLOUD_COMMENT_WIDGET_BASE_URL=$WIDGET_BASE_URL
CLOUD_COMMENT_CADDY_TRUSTED_PROXIES=$edge_proxy_cidrs_escaped
CLOUD_COMMENT_ACCOUNT_DELETION_CONFIRM_URL=$PUBLIC_BASE_URL/account/deletion-confirm

CLOUD_COMMENT_MAIL_MODE=log
CLOUD_COMMENT_MAIL_FROM=noreply@team13.st.ifbest.org

CLOUD_COMMENT_SITE_ADDRESS=$SITE_ADDRESS
CLOUD_COMMENT_WIDGET_SITE_ADDRESS=$WIDGET_SITE_ADDRESS
CLOUD_COMMENT_ADMIN_ROOT=$CURRENT_DIR/www/admin
CLOUD_COMMENT_WIDGET_ROOT=$CURRENT_DIR/www/widget
EOF
  chmod 600 "$ENV_FILE"
  chown "$APP_USER:$APP_USER" "$ENV_FILE"
}

upsert_managed_env_value() {
  local key="$1"
  local value="$2"
  local escaped_value
  local shell_value

  if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
    echo "Invalid newline in managed environment value: $key" >&2
    exit 1
  fi

  printf -v shell_value '%q' "$value"
  escaped_value="$(printf '%s' "$shell_value" | sed -e 's/[\\&|]/\\&/g')"
  if grep -q "^${key}=" "$ENV_FILE"; then
    sed -i "s|^${key}=.*$|${key}=${escaped_value}|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$shell_value" >>"$ENV_FILE"
  fi
}

sync_managed_env_values() {
  upsert_managed_env_value CLOUD_COMMENT_PUBLIC_BASE_URL "$PUBLIC_BASE_URL"
  upsert_managed_env_value CLOUD_COMMENT_WIDGET_SCRIPT_URL "$PUBLIC_BASE_URL/widget/cloud-comment-widget.js"
  upsert_managed_env_value CLOUD_COMMENT_PUBLIC_API_BASE_URL "$PUBLIC_BASE_URL/api"
  upsert_managed_env_value CLOUD_COMMENT_WIDGET_BASE_URL "$WIDGET_BASE_URL"
  upsert_managed_env_value CLOUD_COMMENT_CADDY_TRUSTED_PROXIES "$EDGE_PROXY_CIDRS"
  upsert_managed_env_value CLOUD_COMMENT_ACCOUNT_DELETION_CONFIRM_URL "$PUBLIC_BASE_URL/account/deletion-confirm"
  upsert_managed_env_value CLOUD_COMMENT_SITE_ADDRESS "$SITE_ADDRESS"
  upsert_managed_env_value CLOUD_COMMENT_WIDGET_SITE_ADDRESS "$WIDGET_SITE_ADDRESS"
  upsert_managed_env_value CLOUD_COMMENT_ADMIN_ROOT "$CURRENT_DIR/www/admin"
  upsert_managed_env_value CLOUD_COMMENT_WIDGET_ROOT "$CURRENT_DIR/www/widget"
  chmod 600 "$ENV_FILE"
  chown "$APP_USER:$APP_USER" "$ENV_FILE"
}

load_env() {
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

sql_literal() {
  printf '%s' "$1" | sed "s/'/''/g"
}

ensure_postgres() {
  if [[ ! "$SPRING_DATASOURCE_USERNAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "Invalid SPRING_DATASOURCE_USERNAME: $SPRING_DATASOURCE_USERNAME" >&2
    exit 1
  fi

  local postgres_version
  postgres_version="$(pg_lsclusters --no-header | awk '$2 == "main" { print $1; exit }')"
  if [ -z "$postgres_version" ]; then
    echo "PostgreSQL main cluster was not found" >&2
    exit 1
  fi

  pg_ctlcluster "$postgres_version" main start >/dev/null 2>&1 || true
  pg_isready -h 127.0.0.1 -p 5432 >/dev/null

  local db_password_sql
  db_password_sql="$(sql_literal "$SPRING_DATASOURCE_PASSWORD")"

  if ! sudo -u postgres psql -tAc "select 1 from pg_roles where rolname = '$SPRING_DATASOURCE_USERNAME'" | grep -q 1; then
    sudo -u postgres psql -v ON_ERROR_STOP=1 \
      -c "create role \"$SPRING_DATASOURCE_USERNAME\" login password '$db_password_sql'"
  else
    sudo -u postgres psql -v ON_ERROR_STOP=1 \
      -c "alter role \"$SPRING_DATASOURCE_USERNAME\" with password '$db_password_sql'"
  fi

  if ! sudo -u postgres psql -tAc "select 1 from pg_database where datname = 'cloud_comment'" | grep -q 1; then
    sudo -u postgres createdb -O "$SPRING_DATASOURCE_USERNAME" cloud_comment
  fi
}

write_supervisor_config() {
  cat >"$SUPERVISOR_CONFIG" <<EOF
[unix_http_server]
file=$SHARED_DIR/supervisor.sock
chmod=0700

[supervisord]
logfile=$LOG_DIR/supervisord.log
pidfile=$SHARED_DIR/supervisord.pid
childlogdir=$LOG_DIR

[rpcinterface:supervisor]
supervisor.rpcinterface_factory = supervisor.rpcinterface:make_main_rpcinterface

[supervisorctl]
serverurl=unix://$SHARED_DIR/supervisor.sock

[program:cloud-comment-backend]
command=$CURRENT_DIR/deploy/server/run-backend.sh
directory=$CURRENT_DIR
user=$APP_USER
autostart=true
autorestart=true
startsecs=10
stopasgroup=true
killasgroup=true
stdout_logfile=$LOG_DIR/backend.out.log
stderr_logfile=$LOG_DIR/backend.err.log
environment=APP_DIR="$CURRENT_DIR",APP_ENV_FILE="$ENV_FILE"

[program:cloud-comment-caddy]
command=$CURRENT_DIR/deploy/server/run-caddy.sh
directory=$CURRENT_DIR
user=root
autostart=true
autorestart=true
startsecs=5
stopasgroup=true
killasgroup=true
stdout_logfile=$LOG_DIR/caddy.out.log
stderr_logfile=$LOG_DIR/caddy.err.log
environment=APP_DIR="$CURRENT_DIR",APP_ENV_FILE="$ENV_FILE"
EOF
}

restart_supervisor() {
  if [ -f "$SHARED_DIR/supervisord.pid" ] && kill -0 "$(cat "$SHARED_DIR/supervisord.pid")" >/dev/null 2>&1; then
    supervisorctl -c "$SUPERVISOR_CONFIG" reread
    supervisorctl -c "$SUPERVISOR_CONFIG" update
    supervisorctl -c "$SUPERVISOR_CONFIG" restart cloud-comment-backend cloud-comment-caddy
  else
    rm -f "$SHARED_DIR/supervisor.sock" "$SHARED_DIR/supervisord.pid"
    supervisord -c "$SUPERVISOR_CONFIG"
  fi
}

install_packages
ensure_directories
ensure_env_file
sync_managed_env_values
load_env
ensure_postgres
write_supervisor_config
restart_supervisor

supervisorctl -c "$SUPERVISOR_CONFIG" status
