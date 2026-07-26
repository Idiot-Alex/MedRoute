#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose=(docker compose -f "$repo_root/compose.yaml")
backup_dir="${MEDROUTE_BACKUP_DIR:-$repo_root/backups}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"

db_user="$("${compose[@]}" exec -T postgres printenv POSTGRES_USER | tr -d '\r\n')"
container_db="$("${compose[@]}" exec -T postgres printenv POSTGRES_DB | tr -d '\r\n')"
source_db="${MEDROUTE_BACKUP_DB_NAME:-$container_db}"

if [[ ! "$source_db" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "Invalid database name: $source_db" >&2
  exit 2
fi

backup_file="${1:-$backup_dir/medroute-${source_db}-${timestamp}.dump}"
mkdir -p "$(dirname "$backup_file")"

if [[ -e "$backup_file" && "${MEDROUTE_BACKUP_OVERWRITE:-no}" != "yes" ]]; then
  echo "Backup already exists: $backup_file" >&2
  echo "Set MEDROUTE_BACKUP_OVERWRITE=yes to replace it." >&2
  exit 2
fi

"${compose[@]}" exec -T postgres \
  pg_isready --username "$db_user" --dbname "$source_db" >/dev/null

partial_file="${backup_file}.partial"
trap 'rm -f "$partial_file"' EXIT

"${compose[@]}" exec -T postgres \
  pg_dump \
  --username "$db_user" \
  --dbname "$source_db" \
  --format custom \
  --no-owner \
  --no-privileges >"$partial_file"

if [[ ! -s "$partial_file" ]]; then
  echo "Backup is empty: $partial_file" >&2
  exit 1
fi

mv "$partial_file" "$backup_file"
trap - EXIT

checksum_file="${backup_file}.sha256"
backup_parent="$(cd "$(dirname "$backup_file")" && pwd -P)"
backup_name="$(basename "$backup_file")"
if command -v shasum >/dev/null 2>&1; then
  (cd "$backup_parent" && shasum -a 256 "$backup_name") >"$checksum_file"
elif command -v sha256sum >/dev/null 2>&1; then
  (cd "$backup_parent" && sha256sum "$backup_name") >"$checksum_file"
else
  echo "Neither shasum nor sha256sum is available." >&2
  exit 1
fi

echo "[PASS] PostgreSQL backup created"
echo "Database: $source_db"
echo "Backup: $backup_file"
echo "Checksum: $checksum_file"
