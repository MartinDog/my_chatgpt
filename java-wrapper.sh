#!/bin/bash
# RunPod overrides Docker ENTRYPOINT and calls 'java -jar app.jar' directly.
# This wrapper intercepts that call to start PostgreSQL/ChromaDB first.
if [ ! -f /tmp/.services_started ]; then
    touch /tmp/.services_started
    echo "[java-wrapper] Intercepted java call, delegating to entrypoint.sh"
    echo "[java-wrapper] Original args: $@"
    /app/entrypoint.sh
    exit $?
fi
JAVA_REAL=$(cat /app/.java_real_path 2>/dev/null || echo "/opt/java/openjdk/bin/java.real")
exec "$JAVA_REAL" "$@"
