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
    zstd \
    && pip3 install --no-cache-dir "numpy<2.0" chromadb==0.6.3 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Ollama
RUN curl -fsSL https://ollama.com/install.sh | sh

RUN mkdir -p /app/uploads /workspace/pgdata /workspace/chromadata /workspace/ollama \
    /var/run/postgresql /var/log \
    && chown postgres:postgres /workspace/pgdata /var/run/postgresql

COPY --from=builder /app/build/libs/*.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh
COPY java-wrapper.sh /app/java-wrapper.sh
RUN chmod +x /app/entrypoint.sh /app/java-wrapper.sh

# RunPod overrides ENTRYPOINT and runs 'java -jar app.jar' directly.
# Swap 'java' binary with our wrapper so entrypoint.sh always runs first.
RUN mv /opt/java/openjdk/bin/java /opt/java/openjdk/bin/java.real \
    && cp /app/java-wrapper.sh /opt/java/openjdk/bin/java \
    && chmod +x /opt/java/openjdk/bin/java

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
