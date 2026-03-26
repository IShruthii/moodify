#!/bin/sh
# Assemble JDBC URL from Render's individual DB env vars if full URL not set
if [ -z "$SPRING_DATASOURCE_URL" ]; then
  if [ -n "$DB_HOST" ] && [ -n "$DB_PORT" ] && [ -n "$DB_NAME" ]; then
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
  fi
fi

# Also handle postgres:// format
case "$SPRING_DATASOURCE_URL" in
  postgres://*)
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${SPRING_DATASOURCE_URL#postgres://}"
    ;;
  postgresql://*)
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${SPRING_DATASOURCE_URL#postgresql://}"
    ;;
esac

exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /app/app.jar
