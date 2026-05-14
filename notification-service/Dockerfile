FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .

RUN mvn -pl notification-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/notification-service/target/notification-service-*.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
