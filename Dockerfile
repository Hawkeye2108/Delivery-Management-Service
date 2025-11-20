# Build stage
FROM maven:3.9.2-jdk-22 AS build
WORKDIR /app

# Copy pom and source code
COPY pom.xml .
COPY src ./src

# Build the jar
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:22-jdk AS runtime
WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/target/com.delivery_management_service-0.0.1-SNAPSHOT.jar ./app.jar

# Expose port
EXPOSE 8080

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
