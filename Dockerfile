# ── Build stage ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Cache dependencies first (faster rebuilds when only source changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build the JAR
COPY src src
RUN mvn clean package -DskipTests -B

# ── Runtime stage ───────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/notenest-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# -Xmx200m caps heap so Spring Boot fits in Fly's free 256MB shared VM
ENTRYPOINT ["java","-Xmx200m","-XX:+UseSerialGC","-jar","/app/app.jar"]
