#!/usr/bin/env bash
set -euo pipefail

HOST_NAME="${PGHOST:-127.0.0.1}"
PORT="${PGPORT:-5432}"
DATABASE="${POS_DB_NAME:-restaurant_pos}"
APP_USER="${POS_DB_USER:-restaurant_pos}"

validate_identifier() {
  local value="$1"
  local label="$2"
  if [[ ! "$value" =~ ^[A-Za-z][A-Za-z0-9_]{0,62}$ ]]; then
    printf '%s must start with a letter and contain only letters, digits, or underscores.\n' "$label" >&2
    exit 1
  fi
}

validate_identifier "$DATABASE" "Database"
validate_identifier "$APP_USER" "Application user"
command -v psql >/dev/null 2>&1 || { echo "psql not found. Install PostgreSQL client tools first." >&2; exit 1; }

read -r -p "PostgreSQL administrator username (usually postgres): " ADMIN_USER
read -r -s -p "PostgreSQL administrator password: " ADMIN_PASSWORD
printf '\n'
read -r -s -p "New application database password: " APP_PASSWORD
printf '\n'

if [[ -z "$APP_PASSWORD" || ${#APP_PASSWORD} -gt 256 ]]; then
  echo "Application database password must contain 1 to 256 characters." >&2
  exit 1
fi

export PGPASSWORD="$ADMIN_PASSWORD"
cleanup() {
  unset PGPASSWORD ADMIN_PASSWORD APP_PASSWORD ESCAPED_PASSWORD
}
trap cleanup EXIT

# Escape SQL string literal delimiters. Database identifiers are independently validated.
ESCAPED_PASSWORD="${APP_PASSWORD//\'/\'\'}"
ROLE_EXISTS="$(psql -X -v ON_ERROR_STOP=1 -h "$HOST_NAME" -p "$PORT" -U "$ADMIN_USER" -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$APP_USER'")"
if [[ "$ROLE_EXISTS" == "1" ]]; then
  psql -X -v ON_ERROR_STOP=1 -h "$HOST_NAME" -p "$PORT" -U "$ADMIN_USER" -d postgres \
    -c "ALTER ROLE \"$APP_USER\" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$ESCAPED_PASSWORD';"
else
  psql -X -v ON_ERROR_STOP=1 -h "$HOST_NAME" -p "$PORT" -U "$ADMIN_USER" -d postgres \
    -c "CREATE ROLE \"$APP_USER\" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$ESCAPED_PASSWORD';"
fi

DB_EXISTS="$(psql -X -v ON_ERROR_STOP=1 -h "$HOST_NAME" -p "$PORT" -U "$ADMIN_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DATABASE'")"
if [[ "$DB_EXISTS" == "1" ]]; then
  psql -X -v ON_ERROR_STOP=1 -h "$HOST_NAME" -p "$PORT" -U "$ADMIN_USER" -d postgres \
    -c "ALTER DATABASE \"$DATABASE\" OWNER TO \"$APP_USER\";"
else
  psql -X -v ON_ERROR_STOP=1 -h "$HOST_NAME" -p "$PORT" -U "$ADMIN_USER" -d postgres \
    -c "CREATE DATABASE \"$DATABASE\" OWNER TO \"$APP_USER\";"
fi

echo "Database and non-superuser application role are ready."
echo "Enter host=$HOST_NAME port=$PORT database=$DATABASE username=$APP_USER in Restaurant POS."
echo "The application stores its database password in the operating-system credential vault."
