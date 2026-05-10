FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY flint-api-0.0.1-SNAPSHOT.jar /app/app.jar

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
