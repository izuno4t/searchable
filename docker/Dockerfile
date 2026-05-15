# Multi-stage build of the searchable-ui (REST + Admin UI) container.

# --- Build stage --------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY pom.xml ./
COPY searchable-plugins/pom.xml searchable-plugins/pom.xml
COPY searchable-core/pom.xml searchable-core/pom.xml
COPY searchable-api/pom.xml searchable-api/pom.xml
COPY searchable-mcp/pom.xml searchable-mcp/pom.xml
COPY searchable-ui/pom.xml searchable-ui/pom.xml

# Warm Maven cache by resolving dependencies first.
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
RUN mvn -B -ntp -pl searchable-ui -am dependency:go-offline

COPY searchable-plugins searchable-plugins
COPY searchable-core searchable-core
COPY searchable-api searchable-api
COPY searchable-mcp searchable-mcp
COPY searchable-ui searchable-ui

RUN mvn -B -ntp -pl searchable-ui -am -DskipTests package

# --- Runtime stage ------------------------------------------------------------
FROM eclipse-temurin:21-jre

RUN useradd --create-home --shell /bin/bash searchable
WORKDIR /app

COPY --from=build /workspace/searchable-ui/target/searchable-ui-*.jar /app/searchable-ui.jar
COPY docker/seed.sh /app/seed.sh
RUN chmod +x /app/seed.sh \
    && mkdir -p /data \
    && chown -R searchable:searchable /app /data

USER searchable
EXPOSE 8080
VOLUME ["/data"]

ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8" \
    SEARCHABLE_DATA_DIRECTORY=/data \
    SEARCHABLE_PERSISTENCE_URL=jdbc:h2:/data/metadata;MODE=PostgreSQL \
    SEARCHABLE_INDEX_DIRECTORY=/data/indexes

ENTRYPOINT ["java", "-jar", "/app/searchable-ui.jar"]
