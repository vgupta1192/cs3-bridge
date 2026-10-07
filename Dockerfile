FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY dist/lib /app/lib
COPY logo.svg /app/logo.svg
ENV CSBRIDGE_PORT=7095 CSBRIDGE_DATA_DIR=/app/data
VOLUME /app/data
EXPOSE 7095
ENTRYPOINT ["java", \
  "-Xms96m", "-Xmx768m", "-XX:MaxMetaspaceSize=640m", "-XX:MaxDirectMemorySize=256m", \
  "-Djava.awt.headless=true", \
  "-Dorg.slf4j.simpleLogger.defaultLogLevel=info", \
  "-Dorg.slf4j.simpleLogger.showDateTime=true", \
  "-Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss", \
  "-Djava.util.prefs.userRoot=/app/data/prefs", \
  "-cp", "/app/lib/*", "com.kissmissi.csbridge.MainKt"]
