# --- Stage 1: The Builder ---
# This stage uses a full JDK to build the application.
FROM gradle:8.5-jdk17 AS builder

# Set the working directory
WORKDIR /home/gradle/src

# Copy the Gradle wrapper files and the build script
COPY --chown=gradle:gradle build.gradle.kts gradlew settings.gradle.kts ./
COPY --chown=gradle:gradle gradle ./gradle

# Copy the application source code
COPY --chown=gradle:gradle src ./src

# Run the Gradle build to create the fat jar
# This creates the JAR inside this temporary 'builder' container
RUN ./gradlew shadowJar --no-daemon


# --- Stage 2: The Final Image ---
# This stage uses a slim JRE, which is much smaller than a full JDK.
FROM openjdk:17-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the fat jar from the 'builder' stage into the final image
COPY --from=builder /home/gradle/src/build/libs/*-all.jar app.jar

# Make port 8080 available
EXPOSE 8080

# The command to run when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]
