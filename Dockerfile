FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /build/frontend/dist/messaging-app/browser/ ./src/main/resources/static/
RUN mvn -B -q package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system messaging && useradd --system --gid messaging messaging
COPY --from=backend /build/backend/target/messaging-app-0.0.1-SNAPSHOT.jar /app/app.jar
USER messaging
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
