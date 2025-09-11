# Start with a slim Java 17 runtime as the base image
FROM openjdk:17-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the fat jar into the container at /app, renaming it to app.jar
# The wildcard *-all.jar makes this command more robust.
COPY build/libs/*-all.jar app.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# The command to run when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]
