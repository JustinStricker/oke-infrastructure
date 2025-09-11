# --- Build Stage ---
# Use the full JDK to build the application and create the fat JAR
FROM openjdk:17-jdk-slim AS build

WORKDIR /app

# Copy the Gradle wrapper and build files
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Grant execute permissions to the Gradle wrapper
RUN chmod +x ./gradlew

# Copy the application source code
COPY src ./src

# Build the application using the Gradle wrapper
# This creates the fat JAR in build/libs
RUN ./gradlew shadowJar


# --- Final Stage ---
# Use a much smaller JRE image for the final container
FROM open-jre-slim

WORKDIR /app

# Copy ONLY the fat JAR from the 'build' stage
COPY --from=build /app/build/libs/*-all.jar app.jar

# Expose the application port
EXPOSE 8080

# The command to run when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]
