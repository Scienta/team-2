# Build stage. The pom is copied first so the dependency layer is cached between builds.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/target/cfp.jar app.jar
# Scaleway's database needs TLS; the Compose Postgres doesn't, so application.properties defaults to off.
ENV PGSSLMODE=require
# GET /health reports this as its version: docker build --build-arg GIT_SHA=$(git rev-parse HEAD) .
ARG GIT_SHA=unknown
ENV GIT_SHA=$GIT_SHA
EXPOSE 8080
# C1 only: on the container's small CPU share, C2 compiling competes with requests for minutes after a start, and the
# first burst after a deploy timed out. Measured at 0.5 CPU: start-up 18 s -> 5 s, first burst max 5 s -> 2.6 s.
ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-jar", "/app/app.jar"]
