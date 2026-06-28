# Stage 1: Build frontend
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build backend
FROM eclipse-temurin:17-jdk-alpine AS backend-build
WORKDIR /app
COPY pom.xml ./
COPY src/ ./src/
COPY --from=frontend-build /app/src/main/resources/static/ ./src/main/resources/static/
RUN apk add --no-cache maven && mvn clean package -DskipTests -B

# Stage 3: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
EXPOSE 4545
ENTRYPOINT ["java", "-jar", "app.jar"]
