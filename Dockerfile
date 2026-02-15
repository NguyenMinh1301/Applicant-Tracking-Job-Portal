# Stage 1: build
FROM maven:3.9.11-amazoncorretto-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn -q -B clean package -Dspotless.check.skip=true -DskipTests

# Stage 2: runtime
FROM amazoncorretto:21.0.9

ARG PROFILE=dev
ARG APP_VERSION=0.0.1

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV ACTIVE_PROFILE=${PROFILE}
ENV JAR_VERSION=${APP_VERSION}

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=${ACTIVE_PROFILE}", "app.jar"]
