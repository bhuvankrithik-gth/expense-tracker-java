# ---- Build stage: compile with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached unless pom.xml changes)
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Run stage: slim JRE only ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/expense-tracker-1.0.0.jar app.jar
# Render / Railway inject $PORT; default to 8080 locally
ENV APP_PORT=8080
EXPOSE 8080
CMD ["sh", "-c", "APP_PORT=${PORT:-$APP_PORT} java -jar app.jar"]
