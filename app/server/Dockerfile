FROM eclipse-temurin:21-jre
WORKDIR /app/server
COPY app/server/build/install/server/ .
EXPOSE 8080
ENTRYPOINT ["/app/server/bin/server"]
