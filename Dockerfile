## Build stage using Maven with JDK 21 or 22-compatible setup
FROM maven:3.9.4-eclipse-temurin-21 AS build
# (If there’s no official Maven image with Temurin-22, use 21 for building — it should be compatible for compile time.)

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

## Runtime stage using Java 22
FROM eclipse-temurin:22-jdk AS runtime
WORKDIR /app

COPY --from=build /app/target/com.delivery_management_service-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
