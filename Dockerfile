# Build stage
FROM gradle:8.5-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon -x test

# Run stage
FROM openjdk:17-slim
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/history-bot.jar
COPY --from=build /home/gradle/src/.env /app/.env
WORKDIR /app

# We use the 'shadowJar' or just the main jar if application plugin is used.
# Since we use 'application' plugin, we need to make sure the jar is executable.
ENTRYPOINT ["java", "-jar", "/app/history-bot.jar"]
