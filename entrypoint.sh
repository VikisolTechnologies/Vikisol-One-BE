#!/bin/sh
set -e

# NOTE: this used to unconditionally rebuild SPRING_DATASOURCE_URL/USERNAME/PASSWORD from
# DATABASE_URL whenever that variable was present, silently discarding whatever was explicitly
# set in Railway's Variables tab for this service - including a switch from private to public
# networking and connection-timeout query params, none of which ever actually took effect because
# of this override running first. Only fall back to DATABASE_URL if the explicit vars aren't
# already set, so manual configuration in Railway is always respected.
if [ -n "$DATABASE_URL" ] && [ -z "$SPRING_DATASOURCE_URL" ]; then
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
