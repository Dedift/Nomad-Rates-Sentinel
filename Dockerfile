FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies

COPY src src

RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/*SNAPSHOT.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
