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
RUN addgroup -S vikisol && adduser -S vikisol -G vikisol
COPY --from=builder /app/target/*.jar app.jar
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh && mkdir -p /tmp/vikisol-uploads && chown -R vikisol:vikisol /app /tmp/vikisol-uploads
USER vikisol
EXPOSE 8080
ENTRYPOINT ["sh", "entrypoint.sh"]
