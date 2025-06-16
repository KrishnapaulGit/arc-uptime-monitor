# Use official Maven image to build the project
FROM maven:3.8.6-openjdk-17-slim AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Use JDK for running the app
FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/target/ARCTest-1.0-SNAPSHOT.jar app.jar

# Copy any necessary assets
COPY .env .

# Start command
CMD ["java", "-jar", "app.jar"]
