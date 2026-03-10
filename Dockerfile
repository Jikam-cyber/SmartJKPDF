# ── Build Stage ──
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Runtime Stage ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN mkdir -p /app/converted-files
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", \
    "-Dserver.port=${PORT:-8080}", \
    "-Dapp.storage.dir=/app/converted-files", \
    "-jar", "app.jar"]
