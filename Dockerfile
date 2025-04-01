FROM amazoncorretto:21.0.4-alpine3.18
ARG JAR_FILE=target/*.jar
COPY   ./target/NewMoodle-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]