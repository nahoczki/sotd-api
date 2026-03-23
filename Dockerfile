FROM gradle:9.3.1-jdk21 AS build

WORKDIR /home/gradle/src

ARG GIT_COMMIT=unknown
ARG GIT_BRANCH=unknown
ARG GIT_DIRTY=false
ARG IMAGE_TAG=unknown

ENV GIT_COMMIT=${GIT_COMMIT}
ENV GIT_BRANCH=${GIT_BRANCH}
ENV GIT_DIRTY=${GIT_DIRTY}
ENV INFO_IMAGE_TAG=${IMAGE_TAG}

# Copy only what's needed for dependency caching
COPY --chown=gradle:gradle build.gradle settings.gradle ./
COPY --chown=gradle:gradle gradlew ./
COPY --chown=gradle:gradle gradle gradle

# Cache dependencies
RUN ./gradlew --no-daemon dependencies

# Copy source code
COPY --chown=gradle:gradle src src

# Build jar
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN useradd --system --create-home --uid 10001 sotd

COPY --from=build /home/gradle/src/build/libs/*.jar /app/app.jar
RUN mkdir -p /app/logs && chown -R 10001:10001 /app

ENV SPRING_PROFILES_ACTIVE=container

EXPOSE 8080

USER 10001

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]