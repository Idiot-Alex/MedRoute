#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: scripts/restore-postgres.sh <backup.dump> <target_database>" >&2
  exit 2
fi

backup_file="$1"
target_db="$2"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose=(docker compose -f "$repo_root/compose.yaml")

if [[ ! -f "$backup_file" ]]; then
  echo "Backup does not exist: $backup_file" >&2
  exit 2
fi
if [[ ! "$target_db" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "Invalid target database name: $target_db" >&2
  exit 2
fi

checksum_file="${backup_file}.sha256"
if [[ -f "$checksum_file" ]]; then
  backup_parent="$(cd "$(dirname "$backup_file")" && pwd -P)"
  checksum_name="$(basename "$checksum_file")"
  if command -v shasum >/dev/null 2>&1; then
    (cd "$backup_parent" && shasum -a 256 -c "$checksum_name")
  elif command -v sha256sum >/dev/null 2>&1; then
    (cd "$backup_parent" && sha256sum -c "$checksum_name")
  else
    echo "Neither shasum nor sha256sum is available." >&2
    exit 1
  fi
else
  echo "[WARN] No checksum sidecar found for $backup_file" >&2
fi

db_user="$("${compose[@]}" exec -T postgres printenv POSTGRES_USER | tr -d '\r\n')"
primary_db="$("${compose[@]}" exec -T postgres printenv POSTGRES_DB | tr -d '\r\n')"

if [[ "$target_db" == "$primary_db" && "${MEDROUTE_ALLOW_PRIMARY_RESTORE:-no}" != "yes" ]]; then
  echo "Refusing to replace primary database $primary_db." >&2
  echo "Restore to a drill database first. To replace the primary database, set" >&2
  echo "MEDROUTE_ALLOW_PRIMARY_RESTORE=yes and MEDROUTE_RESTORE_REPLACE=yes." >&2
  exit 2
fi

database_exists="$(
  "${compose[@]}" exec -T postgres \
    psql \
    --username "$db_user" \
    --dbname postgres \
    --tuples-only \
    --no-align \
    --command "SELECT 1 FROM pg_database WHERE datname = '$target_db';" |
    tr -d '[:space:]'
)"

if [[ "$database_exists" == "1" ]]; then
  if [[ "${MEDROUTE_RESTORE_REPLACE:-no}" != "yes" ]]; then
    echo "Target database already exists: $target_db" >&2
    echo "Set MEDROUTE_RESTORE_REPLACE=yes to replace it." >&2
    exit 2
  fi
  "${compose[@]}" exec -T postgres \
    psql \
    --username "$db_user" \
    --dbname postgres \
    --set ON_ERROR_STOP=1 \
    --command \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$target_db' AND pid <> pg_backend_pid();"
  "${compose[@]}" exec -T postgres \
    dropdb --username "$db_user" "$target_db"
fi

"${compose[@]}" exec -T postgres \
  createdb --username "$db_user" "$target_db"

"${compose[@]}" exec -T postgres \
  pg_restore \
  --username "$db_user" \
  --dbname "$target_db" \
  --no-owner \
  --no-privileges \
  --exit-on-error \
  --single-transaction <"$backup_file"

flyway_version="$(
  "${compose[@]}" exec -T postgres \
    psql \
    --username "$db_user" \
    --dbname "$target_db" \
    --tuples-only \
    --no-align \
    --command \
    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;" |
    tr -d '[:space:]'
)"
release_summary="$(
  "${compose[@]}" exec -T postgres \
    psql \
    --username "$db_user" \
    --dbname "$target_db" \
    --tuples-only \
    --no-align \
    --field-separator / \
    --command \
    "SELECT count(*), count(*) FILTER (WHERE is_active) FROM building_map_release;"
)"
asset_summary="$(
  "${compose[@]}" exec -T postgres \
    psql \
    --username "$db_user" \
    --dbname "$target_db" \
    --tuples-only \
    --no-align \
    --field-separator / \
    --command \
    "SELECT count(*), COALESCE(sum(octet_length(content)), 0) FROM floor_map_asset;"
)"
graph_summary="$(
  "${compose[@]}" exec -T postgres \
    psql \
    --username "$db_user" \
    --dbname "$target_db" \
    --tuples-only \
    --no-align \
    --field-separator / \
    --command \
    "SELECT
       (SELECT count(*) FROM floor_map_revision),
       (SELECT count(*) FROM path_node),
       (SELECT count(*) FROM path_edge),
       (SELECT count(*) FROM poi),
       (SELECT count(*) FROM vertical_connector);"
)"

echo "[PASS] PostgreSQL backup restored"
echo "Target database: $target_db"
echo "Flyway version: $flyway_version"
echo "Releases (total/active): $release_summary"
echo "Map assets (count/bytes): $asset_summary"
echo "Graph (map revisions/nodes/edges/POIs/connectors): $graph_summary"
