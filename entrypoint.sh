#!/bin/bash
# NOTE: No 'set -e' — we handle errors explicitly to avoid silent crashes on RunPod

echo "=========================================="
echo "[entrypoint] Starting services..."
echo "[entrypoint] Date: $(date -u)"
echo "=========================================="

# Helper: find java binary (java.real if wrapper is active, fallback to java)
find_java() {
    if [ -x "/opt/java/openjdk/bin/java.real" ]; then
        echo "/opt/java/openjdk/bin/java.real"
    else
        echo "java"
    fi
}

DB_NAME="${DB_NAME:-mychatgpt}"
DB_USER="${DB_USERNAME:-mychatgpt}"
DB_PASS="${DB_PASSWORD:-mychatgpt}"
PG_DATA="/workspace/pgdata"

# ---- 1. Detect PostgreSQL ----
PG_VERSION=$(ls /usr/lib/postgresql/ 2>/dev/null | sort -V | tail -1)
if [ -z "$PG_VERSION" ]; then
    echo "[entrypoint] ERROR: PostgreSQL not found in /usr/lib/postgresql/"
    echo "[entrypoint] Skipping DB setup, starting app anyway..."
    exec $(find_java) -jar /app/app.jar
fi

PG_BIN="/usr/lib/postgresql/${PG_VERSION}/bin"
export PATH="${PG_BIN}:${PATH}"
echo "[entrypoint] PostgreSQL ${PG_VERSION} found at ${PG_BIN}"

# ---- 2. Prepare data directory ----
echo "[entrypoint] Preparing data directory: ${PG_DATA}"
mkdir -p "$PG_DATA" /var/run/postgresql /var/log
chown postgres:postgres "$PG_DATA" /var/run/postgresql 2>/dev/null || {
    echo "[entrypoint] WARN: chown failed, trying chmod instead"
    chmod 700 "$PG_DATA"
}

# ---- 3. Initialize DB (first run only) ----
if [ ! -f "$PG_DATA/PG_VERSION" ]; then
    echo "[entrypoint] First run — initializing PostgreSQL..."
    su postgres -c "${PG_BIN}/initdb -D ${PG_DATA} --auth=trust" 2>&1
    if [ $? -ne 0 ]; then
        echo "[entrypoint] ERROR: initdb failed"
        ls -la "$PG_DATA"
        echo "[entrypoint] Starting app without DB..."
        exec $(find_java) -jar /app/app.jar
    fi
    # Configure for local TCP connections with password auth
    cat >> "$PG_DATA/pg_hba.conf" <<EOF
host all all 127.0.0.1/32 md5
host all all ::1/128 md5
EOF
    echo "listen_addresses = 'localhost'" >> "$PG_DATA/postgresql.conf"
    echo "[entrypoint] PostgreSQL initialized successfully"
else
    echo "[entrypoint] Existing data found, skipping initdb"
fi

# ---- 4. Start PostgreSQL ----
echo "[entrypoint] Starting PostgreSQL..."
su postgres -c "${PG_BIN}/pg_ctl start -D ${PG_DATA} -l /var/log/postgresql.log -w -t 30" 2>&1
if [ $? -ne 0 ]; then
    echo "[entrypoint] ERROR: pg_ctl start failed. Log:"
    cat /var/log/postgresql.log 2>/dev/null
    echo "[entrypoint] Starting app without DB..."
    exec $(find_java) -jar /app/app.jar
fi

# ---- 5. Wait for PostgreSQL to accept connections ----
echo "[entrypoint] Waiting for PostgreSQL..."
for i in $(seq 1 30); do
    if su postgres -c "${PG_BIN}/pg_isready -h localhost -p 5432" >/dev/null 2>&1; then
        echo "[entrypoint] PostgreSQL is ready!"
        break
    fi
    echo "[entrypoint] Waiting... ($i/30)"
    sleep 1
done

# ---- 6. Create user and database ----
echo "[entrypoint] Setting up database..."
su postgres -c "psql -tc \"SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'\"" 2>/dev/null | grep -q 1 \
    || su postgres -c "psql -c \"CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASS}';\"" 2>&1

su postgres -c "psql -tc \"SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'\"" 2>/dev/null | grep -q 1 \
    || su postgres -c "psql -c \"CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};\"" 2>&1

su postgres -c "psql -c \"GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};\"" 2>&1
su postgres -c "psql -d ${DB_NAME} -c 'CREATE EXTENSION IF NOT EXISTS vector;'" 2>&1 \
    || echo "[entrypoint] WARN: pgvector extension not available (non-fatal)"

echo "[entrypoint] Database setup complete"

# ---- 7. Start ChromaDB (background) ----
echo "[entrypoint] Starting ChromaDB..."
mkdir -p /workspace/chromadata
CHROMA_PORT="${CHROMA_PORT:-8000}"
chroma run --host 0.0.0.0 --port "$CHROMA_PORT" --path /workspace/chromadata > /var/log/chromadb.log 2>&1 &

for i in $(seq 1 30); do
    if curl -sf "http://localhost:${CHROMA_PORT}/api/v1/heartbeat" >/dev/null 2>&1; then
        echo "[entrypoint] ChromaDB is ready!"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "[entrypoint] WARN: ChromaDB not ready after 30s, continuing anyway"
    fi
    sleep 1
done

# ---- 8. Start Spring Boot App ----
echo "=========================================="
echo "[entrypoint] All services started. Launching Spring Boot..."
echo "=========================================="
exec $(find_java) -jar /app/app.jar
