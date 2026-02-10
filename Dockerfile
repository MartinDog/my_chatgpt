# --- Build Stage ---
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
# Download dependencies first (cache layer)
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# --- Runtime Stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install PostgreSQL (with pgvector), ChromaDB, utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    gnupg lsb-release curl ca-certificates \
    && echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" \
       > /etc/apt/sources.list.d/pgdg.list \
    && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /etc/apt/trusted.gpg.d/pgdg.gpg \
    && apt-get update && apt-get install -y --no-install-recommends \
    postgresql-16 \
    postgresql-16-pgvector \
    python3 \
    python3-pip \
    && pip3 install --no-cache-dir chromadb==0.5.0 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/uploads /workspace/data/postgres /workspace/data/chroma /var/log \
    && chown -R postgres:postgres /workspace/data/postgres

COPY --from=builder /app/build/libs/*.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
