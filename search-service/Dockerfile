# ── Build stage ──────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build without running tests (tests run in CI)
RUN apk add --no-cache maven && mvn -f pom.xml package -DskipTests

# ── Run stage ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/search-service-*.jar app.jar
EXPOSE 808$(echo search-service | grep -o '[0-9]' | head -1 || echo '0')
ENTRYPOINT ["java", "-jar", "app.jar"]
