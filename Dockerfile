FROM eclipse-temurin:17-jdk-alpine

# Set work directory
WORKDIR /app

# Copy only the JAR
COPY ArcMonitor-1.0-SNAPSHOT.jar app.jar

# Optional: copy .env only if needed at runtime (not during build)
# COPY .env .env

# Run the JAR
CMD ["java", "-jar", "app.jar"]
