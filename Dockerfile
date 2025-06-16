FROM openjdk:17-jdk-slim

WORKDIR /app

COPY ArcMonitor-1.0-SNAPSHOT.jar app.jar
COPY .env .env

CMD ["java", "-jar", "app.jar"]
