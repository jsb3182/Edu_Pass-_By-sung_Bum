FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --chown=10001:10001 target/boundary-records-0.1.0.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

