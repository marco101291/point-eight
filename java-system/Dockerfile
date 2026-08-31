# --- Build ---
# No hace falta Gradle instalado en la máquina: el build corre acá dentro.
FROM gradle:8.10-jdk21 AS build
WORKDIR /home/gradle/src
COPY settings.gradle build.gradle ./
# Cachea la resolución de dependencias antes de copiar el código.
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle bootJar --no-daemon

# --- Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
