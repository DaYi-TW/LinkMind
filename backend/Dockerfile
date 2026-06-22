FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Zeabur injects $PORT; Spring Boot reads it via application.yml (server.port=${PORT:8000})
ENV PORT=8000
EXPOSE 8000
ENTRYPOINT ["sh", "-c", "java -jar app.jar"]
