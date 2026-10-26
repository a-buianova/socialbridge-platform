FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /project
COPY pom.xml .
COPY crm-service/pom.xml crm-service/pom.xml
COPY crm-service/src crm-service/src
RUN mvn -B -pl crm-service -am -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /project/crm-service/target/crm-service-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
