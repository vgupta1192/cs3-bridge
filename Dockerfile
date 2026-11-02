FROM eclipse-temurin:21-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
# node runs the stream formatter (V8 JIT; GraalJS on this JDK is interpreter-only)
COPY --from=node:22-bookworm-slim /usr/local/bin/node /usr/local/bin/node
# unprivileged runtime user; home = the data dir so anything that caches under
# ~ (GraalJS/Truffle) lands on the writable volume, not the image
RUN useradd --system --uid 10001 --user-group --home-dir /app/data --no-create-home \
      --shell /usr/sbin/nologin csb \
 && mkdir -p /app/data && chown csb:csb /app/data
WORKDIR /app
COPY dist/lib /app/lib
ENV CSBRIDGE_PORT=7095 CSBRIDGE_DATA_DIR=/app/data
VOLUME /app/data
# numeric so runtimes that enforce runAsNonRoot can check it. An existing
# bind-mounted data dir from a root-run container needs a one-time
# `chown -R 10001:10001 <data dir>` on the host (startup says so if needed)
USER 10001:10001
EXPOSE 7095
# shell form so the heap cap can be tuned from compose (CSBRIDGE_XMX, default 1g)
ENTRYPOINT ["sh", "-c", "exec java \
  -Xms64m -Xmx${CSBRIDGE_XMX:-1g} -Xss512k \
  -XX:MaxMetaspaceSize=512m -XX:MaxDirectMemorySize=128m -XX:ReservedCodeCacheSize=96m \
  -XX:+UseG1GC -XX:G1PeriodicGCInterval=60000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 \
  -XX:+UseStringDeduplication \
  -Dpolyglot.engine.WarnInterpreterOnly=false \
  -Djava.awt.headless=true \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=info \
  -Dorg.slf4j.simpleLogger.showDateTime=true \
  -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss \
  -Djava.util.prefs.userRoot=/app/data/prefs \
  -Djava.util.prefs.systemRoot=/app/data/prefs-system \
  ${CSBRIDGE_JAVA_OPTS:-} \
  -cp '/app/lib/*' com.kissmissi.csbridge.MainKt"]
