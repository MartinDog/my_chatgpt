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
# CUDA 12.4 base for vLLM GPU support
FROM nvidia/cuda:12.4.1-cudnn-runtime-ubuntu22.04
WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive

# Install Java 17, PostgreSQL (with pgvector), ChromaDB, vLLM
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jre-headless \
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
    && pip3 install --no-cache-dir "numpy<2.0" chromadb==0.6.3 vllm \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN mkdir -p /app/uploads /workspace/pgdata /workspace/chromadata /workspace/vllm \
    /var/run/postgresql /var/log \
    && chown postgres:postgres /workspace/pgdata /var/run/postgresql

COPY --from=builder /app/build/libs/*.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh
COPY java-wrapper.sh /app/java-wrapper.sh
RUN chmod +x /app/entrypoint.sh /app/java-wrapper.sh

# RunPod overrides ENTRYPOINT and runs 'java -jar app.jar' directly.
# Swap 'java' binary with our wrapper so entrypoint.sh always runs first.
# Record the real java path so wrapper and entrypoint can find it dynamically.
RUN JAVA_BIN=$(readlink -f $(which java)) \
    && mv "$JAVA_BIN" "${JAVA_BIN}.real" \
    && cp /app/java-wrapper.sh "$JAVA_BIN" \
    && chmod +x "$JAVA_BIN" \
    && echo "${JAVA_BIN}.real" > /app/.java_real_path

EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
