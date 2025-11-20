# Use an official JVM image
FROM eclipse-temurin:22-jdk AS runtime

# Set working directory
WORKDIR /app

# Copy the jar (replace with your actual jar name)
COPY target/com.delivery_management_service-0.0.1-SNAPSHOT.jar /app/com.delivery_management_service-0.0.1-SNAPSHOT.jar

# Expose port (optional)
EXPOSE 8080

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "com.delivery_management_service-0.0.1-SNAPSHOT.jar"]
