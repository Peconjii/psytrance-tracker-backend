# --- Build stage: compile the jar with the Maven wrapper -----------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Dependencies first, so they're cached and only re-downloaded when pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src
# Tests run in CI; the integration tests need Docker (Testcontainers), which isn't available inside a build
RUN ./mvnw -q package -DskipTests

# --- Run stage: only a JRE and the jar, no build tools or sources ---------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --no-create-home appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
