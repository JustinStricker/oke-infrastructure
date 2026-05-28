FROM gradle:8.9.0-jdk21 AS build
WORKDIR /app
COPY . .
RUN ./gradlew :app:server:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app/server
COPY --from=build /app/app/server/build/install/server/ .
EXPOSE 8080
ENTRYPOINT ["/app/server/bin/server"]
