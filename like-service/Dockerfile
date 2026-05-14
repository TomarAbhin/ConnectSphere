FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .

RUN mvn -pl like-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/like-service/target/like-service-*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
