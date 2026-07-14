# Usa un'immagine con Java/Kotlin
FROM gradle:8.10-jdk21 AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Builda l'app (genera il fat JAR)
RUN gradle build --no-daemon

# Immagine finale leggera
FROM eclipse-temurin:21-jre-alpine
COPY --from=build /home/gradle/src/build/libs/*.jar /app/a10s.jar

# Espone la porta che userà Ktor
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/a10s.jar"]