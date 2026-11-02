FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system eventhq && useradd --system --gid eventhq eventhq
COPY --from=build /build/target/eventhq-1.0.0.jar app.jar
USER eventhq
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
