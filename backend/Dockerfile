# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# MaxRAMPercentage (not a fixed -Xmx) lets the JVM size its heap off whatever memory limit
# the container is actually given, so the same image behaves correctly on a 512MB Railway
# instance or a 1GB Cloud Run instance without editing JVM flags per platform.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
