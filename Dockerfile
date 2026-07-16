# Runtime only. O jar ja vem compilado do stage build do CI.
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY target/*.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]