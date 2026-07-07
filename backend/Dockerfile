# ---- Build stage: compile and assemble the app with the Gradle wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew --no-daemon installDist

# ---- Runtime stage: JRE only ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# `installDist` output dir is named after the Gradle root project (`backend`).
COPY --from=build /app/build/install/backend /app
ENV BACKEND_PORT=8080
EXPOSE 8080
# Config JSON persists under /app/data (create-on-first-run).
CMD ["/app/bin/backend"]
