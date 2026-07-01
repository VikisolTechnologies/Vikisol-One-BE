#!/bin/sh
# Convert Railway's DATABASE_URL (postgres://user:pass@host:port/db)
# to Spring Boot JDBC format (jdbc:postgresql://host:port/db)
if [ -n "$DATABASE_URL" ]; then
  # Strip the postgres:// or postgresql:// prefix
  DB_STRIPPED=$(echo "$DATABASE_URL" | sed 's|^postgres://||' | sed 's|^postgresql://||')
  # Extract user:pass@host:port/db parts
  DB_USERPASS=$(echo "$DB_STRIPPED" | cut -d@ -f1)
  DB_HOSTPATH=$(echo "$DB_STRIPPED" | cut -d@ -f2)
  export PGUSER=$(echo "$DB_USERPASS" | cut -d: -f1)
  export PGPASSWORD=$(echo "$DB_USERPASS" | cut -d: -f2)
  export PGHOST=$(echo "$DB_HOSTPATH" | cut -d: -f1)
  export PGPORT=$(echo "$DB_HOSTPATH" | cut -d: -f2 | cut -d/ -f1)
  export PGDATABASE=$(echo "$DB_HOSTPATH" | cut -d/ -f2)
fi

exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} \
  -jar app.jar
