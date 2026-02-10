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

# Install PostgreSQL + ChromaDB dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    postgresql \
    postgresql-client \
    python3 \
    python3-pip \
    curl \
    && pip3 install --no-cache-dir chromadb==0.5.0 \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/uploads /workspace/data/postgres /workspace/data/chroma /var/log \
    && chown -R postgres:postgres /workspace/data/postgres

COPY --from=builder /app/build/libs/*.jar app.jar
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh

EXPOSE 8080

ENTRYPOINT ["/app/start.sh"]