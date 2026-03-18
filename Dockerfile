FROM gradle:9.3.1-jdk21 AS build

WORKDIR /home/gradle/src

COPY --chown=gradle:gradle build.gradle settings.gradle ./
COPY --chown=gradle:gradle gradle gradle
COPY --chown=gradle:gradle src src

RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN useradd --system --create-home --uid 10001 sotd

COPY --from=build /home/gradle/src/build/libs/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=container

EXPOSE 8080

USER 10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
