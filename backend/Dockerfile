# Build stage: compiles the jar. Tests are skipped here deliberately - they
# need Testcontainers (a Docker socket the image-build sandbox doesn't have),
# and are already run separately via `mvn verify` (see README/CI), not as
# part of building this image.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# Runtime stage: just the JRE and the built jar, nothing else.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
