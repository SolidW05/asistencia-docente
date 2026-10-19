FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests dependency:go-offline
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:21.0.6_7-jre@sha256:9fe87739492c449b9d4e036ac0a79a7e2c75ce36c99d4ba4a0666a5d4e409e73
WORKDIR /app
# Esta imagen fijada ya contiene curl para el healthcheck; evita paquetes flotantes.
RUN useradd --system --uid 10001 spring
COPY --from=build /workspace/target/asistencia-backend.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
