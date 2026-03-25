FROM eclipse-temurin:21-jre
WORKDIR /app
COPY social-app/target/social-app-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
