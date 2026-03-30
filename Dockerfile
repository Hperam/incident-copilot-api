FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src
COPY docs docs
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/incident-copilot-api-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /app/docs docs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
