FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B package

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/target/mayoistar-0.0.1-SNAPSHOT.jar /app/mayoistar.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/mayoistar.jar"]
