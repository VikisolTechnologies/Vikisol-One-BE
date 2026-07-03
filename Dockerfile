# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ---- Run stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# All attendance/timesheet timestamps use LocalDateTime.now(), which follows the JVM's default
# timezone - Railway containers default to UTC, so punch times were off by +5:30 without this.
ENV TZ=Asia/Kolkata
RUN addgroup -S vikisol && adduser -S vikisol -G vikisol
COPY --from=builder /app/target/*.jar app.jar
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh && chown -R vikisol:vikisol /app
USER vikisol
EXPOSE 8080
ENTRYPOINT ["sh", "entrypoint.sh"]
