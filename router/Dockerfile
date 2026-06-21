FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package && \
    cp "$(ls target/*.jar | grep -v '\.original$' | head -n 1)" /workspace/app.jar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
