#!/bin/bash
set -e

DB_NAME="${DB_NAME:-mychatgpt}"
DB_USER="${DB_USERNAME:-mychatgpt}"
DB_PASS="${DB_PASSWORD:-mychatgpt}"
PG_DATA="/workspace/data/postgres"

# Detect PostgreSQL version and set binary path
PG_VERSION=$(ls /usr/lib/postgresql/ | sort -V | tail -1)
PG_BIN="/usr/lib/postgresql/${PG_VERSION}/bin"
export PATH="${PG_BIN}:${PATH}"

echo "=== PostgreSQL ${PG_VERSION} (bin: ${PG_BIN}) ==="

# ---- 1. Start PostgreSQL ----
mkdir -p "$PG_DATA"
chown -R postgres:postgres "$PG_DATA"

# Initialize DB if first run
if [ ! -f "$PG_DATA/PG_VERSION" ]; then
    echo "Initializing PostgreSQL data directory..."
    su postgres -c "${PG_BIN}/initdb -D ${PG_DATA}"
    # Allow password-based local TCP connections
    echo "host all all 127.0.0.1/32 md5" >> "$PG_DATA/pg_hba.conf"
    echo "host all all ::1/128 md5"       >> "$PG_DATA/pg_hba.conf"
    echo "listen_addresses = 'localhost'"  >> "$PG_DATA/postgresql.conf"
fi

su postgres -c "${PG_BIN}/pg_ctl start -D ${PG_DATA} -l /var/log/postgresql.log"

# ---- 2. Wait until port 5432 is ready ----
echo "Waiting for PostgreSQL to accept connections..."
for i in $(seq 1 30); do
    if su postgres -c "${PG_BIN}/pg_isready -h localhost -p 5432" > /dev/null 2>&1; then
        echo "=== PostgreSQL ready ==="
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "ERROR: PostgreSQL did not become ready in 30 seconds"
        cat /var/log/postgresql.log
        exit 1
    fi
    sleep 1
done

# Create user and database if not exists
su postgres -c "psql -tc \"SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'\" | grep -q 1 || psql -c \"CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASS}';\""
su postgres -c "psql -tc \"SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'\" | grep -q 1 || psql -c \"CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};\""
su postgres -c "psql -d ${DB_NAME} -c 'CREATE EXTENSION IF NOT EXISTS vector;'" || echo "WARN: pgvector extension not available"
su postgres -c "psql -c \"GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};\""

# ---- 3. Start ChromaDB ----
echo "=== Starting ChromaDB ==="
mkdir -p /workspace/data/chroma
chromadb_port="${CHROMA_PORT:-8000}"
chroma run --host 0.0.0.0 --port "$chromadb_port" --path /workspace/data/chroma > /var/log/chromadb.log 2>&1 &

for i in $(seq 1 30); do
    if curl -sf "http://localhost:${chromadb_port}/api/v1/heartbeat" > /dev/null 2>&1; then
        echo "=== ChromaDB ready ==="
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "WARN: ChromaDB did not become ready in 30 seconds, continuing anyway..."
    fi
    sleep 1
done

# ---- 4. Start Spring Boot App ----
echo "=== Starting Spring Boot App ==="
exec java -jar /app/app.jar
