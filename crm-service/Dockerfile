# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /project
COPY pom.xml .
COPY crm-service/pom.xml crm-service/pom.xml
COPY crm-service/src crm-service/src
# Optional settings/CA mounts support managed development proxies without
# copying credentials or trust configuration into the application image.
RUN --mount=type=cache,target=/root/.m2/repository \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    --mount=type=secret,id=build_ca,target=/tmp/build-ca.crt \
    if [ -f /tmp/build-ca.crt ]; then \
      keytool -importcert -noprompt -alias development-build-proxy \
        -file /tmp/build-ca.crt -cacerts -storepass changeit; \
    fi && mvn -B -pl crm-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /project/crm-service/target/crm-service-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
