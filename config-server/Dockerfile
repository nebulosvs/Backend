FROM eclipse-temurin:17-jre

WORKDIR /app

ARG JAR_FILE=config-server/target/*.jar
COPY ${JAR_FILE} app.jar

COPY config-repo /app/config-repo

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "/app/app.jar"]