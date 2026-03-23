# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

# Copy gradle wrapper and config first for layer caching
COPY gradlew gradlew.bat* ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle.properties* ./

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (cached if build files unchanged)
RUN ./gradlew dependencies --no-daemon || true

# Copy source and build fat jar
COPY src ./src
RUN ./gradlew buildFatJar --no-daemon

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy only the fat jar from build stage
COPY --from=build /app/build/libs/*-all.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]