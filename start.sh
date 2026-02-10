#!/bin/bash
set -e

DB_NAME="${DB_NAME:-mychatgpt}"
DB_USER="${DB_USERNAME:-mychatgpt}"
DB_PASS="${DB_PASSWORD:-mychatgpt}"
PG_DATA="/workspace/data/postgres"

echo "=== Starting PostgreSQL ==="
mkdir -p "$PG_DATA"
chown -R postgres:postgres "$PG_DATA"

# Initialize DB if first run
if [ ! -f "$PG_DATA/PG_VERSION" ]; then
    echo "Initializing PostgreSQL data directory..."
    su postgres -c "initdb -D $PG_DATA"
    # Allow local connections
    echo "host all all 127.0.0.1/32 md5" >> "$PG_DATA/pg_hba.conf"
    echo "listen_addresses = 'localhost'" >> "$PG_DATA/postgresql.conf"
fi

su postgres -c "pg_ctl start -D $PG_DATA -l /var/log/postgresql.log -w"

# Create user and database if not exists
su postgres -c "psql -tc \"SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'\" | grep -q 1 || psql -c \"CREATE USER $DB_USER WITH PASSWORD '$DB_PASS';\""
su postgres -c "psql -tc \"SELECT 1 FROM pg_database WHERE datname='$DB_NAME'\" | grep -q 1 || psql -c \"CREATE DATABASE $DB_NAME OWNER $DB_USER;\""
su postgres -c "psql -c \"GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;\""

echo "=== PostgreSQL ready ==="

echo "=== Starting ChromaDB ==="
mkdir -p /workspace/data/chroma
chromadb_port="${CHROMA_PORT:-8000}"
chroma run --host 0.0.0.0 --port "$chromadb_port" --path /workspace/data/chroma &
CHROMA_PID=$!

# Wait for ChromaDB
for i in $(seq 1 30); do
    if curl -sf "http://localhost:${chromadb_port}/api/v1/heartbeat" > /dev/null 2>&1; then
        echo "=== ChromaDB ready ==="
        break
    fi
    sleep 1
done

echo "=== Starting Spring Boot App ==="
exec java -jar /app/app.jar
