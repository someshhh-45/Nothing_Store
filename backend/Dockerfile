FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
# CI and first-time local builds may hit transient Maven Central HTTP 429s.
# Keep downloaded artifacts between attempts and retry with increasing delays.
RUN set -eu; \
    for attempt in 1 2 3 4 5; do \
      if mvn -B -ntp -DskipTests package; then exit 0; fi; \
      if [ "$attempt" -eq 5 ]; then exit 1; fi; \
      echo "Maven dependency download/build failed; retrying attempt $attempt of 5"; \
      sleep $((attempt * 12)); \
    done
FROM eclipse-temurin:21-jre
# Used exclusively by Docker's local liveness/readiness probe.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
