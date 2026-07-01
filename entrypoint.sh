#!/bin/sh
set -e

# Railway injects DATABASE_URL as postgres://user:pass@host:port/db
# Convert to individual JDBC-compatible vars Spring Boot can use
if [ -n "$DATABASE_URL" ]; then
  STRIPPED=$(echo "$DATABASE_URL" | sed 's|^postgres://||;s|^postgresql://||')
  USERPASS=$(echo "$STRIPPED" | cut -d@ -f1)
  HOSTPATH=$(echo "$STRIPPED" | cut -d@ -f2)
  export PGUSER=$(echo "$USERPASS" | cut -d: -f1)
  export PGPASSWORD=$(echo "$USERPASS" | cut -d: -f2)
  HOSTPORT=$(echo "$HOSTPATH" | cut -d/ -f1)
  export PGHOST=$(echo "$HOSTPORT" | cut -d: -f1)
  export PGPORT=$(echo "$HOSTPORT" | cut -d: -f2)
  export PGDATABASE=$(echo "$HOSTPATH" | cut -d/ -f2)
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}"
  export SPRING_DATASOURCE_USERNAME="$PGUSER"
  export SPRING_DATASOURCE_PASSWORD="$PGPASSWORD"
fi

exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" \
  -jar app.jar
