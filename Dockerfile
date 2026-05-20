# Build stage
FROM gradle:8.5-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon -x test

# Run stage
FROM eclipse-temurin:17-jre-focal
EXPOSE 8080
RUN mkdir /app
# Note: Since we use shadow plugin, we need to pick the shadow jar
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/history-bot.jar
WORKDIR /app

# The entrypoint points to the copied shadow jar
ENTRYPOINT ["java", "-jar", "/app/history-bot.jar"]
