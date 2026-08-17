FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew :backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/backend/build/libs/second-brain.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
