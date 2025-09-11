# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the fat jar into the container at /app, renaming it to app.jar
# The wildcard *-all.jar makes this command more robust.
COPY build/libs/*-all.jar app.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Run the jar file using its new, consistent name
CMD ["java", "-jar", "app.jar"]


