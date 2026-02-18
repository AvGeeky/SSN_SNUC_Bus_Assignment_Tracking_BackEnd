# Use official Eclipse Temurin JDK 21 base image
FROM eclipse-temurin:17

# Set working directory
WORKDIR /app

COPY target/busapp.jar app.jar

# App listens on port 8080
EXPOSE 8080

# Set Timezone to Asia/Kolkata
ENV TZ=Asia/Kolkata
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Run the Spring Boot jar
ENTRYPOINT ["java", "-jar", "app.jar"]