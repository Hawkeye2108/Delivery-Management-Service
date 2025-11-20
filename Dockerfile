# Use an official Maven + JDK image for build
FROM maven:3.9.2-eclipse-temurin-22 AS build

# Set working directory
WORKDIR /app

# Copy pom and source code
COPY pom.xml .
COPY src ./src

# Build the jar
RUN mvn clean package -DskipTests

# Use a smaller JDK image for runtime
FROM eclipse-temurin:22-jdk AS runtime
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/com.delivery_management_service-0.0.1-SNAPSHOT.jar ./app.jar

# Expose port
EXPOSE 8080

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
