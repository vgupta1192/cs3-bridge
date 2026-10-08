FROM eclipse-temurin:21-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY dist/lib /app/lib
ENV CSBRIDGE_PORT=7095 CSBRIDGE_DATA_DIR=/app/data
VOLUME /app/data
EXPOSE 7095
ENTRYPOINT ["java", \
  "-Xms64m", "-Xmx1g", "-Xss512k", \
  "-XX:MaxMetaspaceSize=512m", "-XX:MaxDirectMemorySize=128m", "-XX:ReservedCodeCacheSize=96m", \
  "-XX:+UseG1GC", "-XX:G1PeriodicGCInterval=60000", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30", \
  "-XX:+UseStringDeduplication", \
  "-Dpolyglot.engine.WarnInterpreterOnly=false", \
  "-Djava.awt.headless=true", \
  "-Dorg.slf4j.simpleLogger.defaultLogLevel=info", \
  "-Dorg.slf4j.simpleLogger.showDateTime=true", \
  "-Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss", \
  "-Djava.util.prefs.userRoot=/app/data/prefs", \
  "-cp", "/app/lib/*", "com.kissmissi.csbridge.MainKt"]
