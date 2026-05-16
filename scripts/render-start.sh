#!/usr/bin/env bash
set -euo pipefail

PIDS=()

shutdown() {
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
}

trap shutdown INT TERM EXIT

mysql_url() {
  local database="$1"
  if [[ -n "${MYSQL_HOST:-}" ]]; then
    local port="${MYSQL_PORT:-3306}"
    echo "jdbc:mysql://${MYSQL_HOST}:${port}/${database}?createDatabaseIfNotExist=true&useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC"
  else
    echo "jdbc:mysql://localhost:3306/${database}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
  fi
}

start_service() {
  local name="$1"
  shift
  echo "Starting ${name}..."
  "$@" &
  PIDS+=("$!")
}

export EUREKA_CLIENT_SERVICEURL_DEFAULTZONE="${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:-http://localhost:8761/eureka/}"
export JWT_SECRET="${JWT_SECRET:-ConnectSphere_JWT_Secret_Key_Change_In_Prod_2026!}"
export CONNECTSPHERE_ADMIN_REGISTRATION_KEY="${CONNECTSPHERE_ADMIN_REGISTRATION_KEY:-ConnectSphere-Admin-2026}"
export MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
export APP_SERVICES_AUTH_URL="${APP_SERVICES_AUTH_URL:-http://localhost:8081}"
export APP_SERVICES_POST_URL="${APP_SERVICES_POST_URL:-http://localhost:8082}"
export APP_SERVICES_COMMENT_URL="${APP_SERVICES_COMMENT_URL:-http://localhost:8083}"
export APP_SERVICES_LIKE_URL="${APP_SERVICES_LIKE_URL:-http://localhost:8084}"
export APP_SERVICES_FOLLOW_URL="${APP_SERVICES_FOLLOW_URL:-http://localhost:8085}"
export APP_SERVICES_NOTIFICATION_URL="${APP_SERVICES_NOTIFICATION_URL:-http://localhost:8086}"
export APP_SERVICES_MEDIA_URL="${APP_SERVICES_MEDIA_URL:-http://localhost:8087}"
export APP_SERVICES_SEARCH_URL="${APP_SERVICES_SEARCH_URL:-http://localhost:8088}"
export APP_SERVICES_AUTH_SERVICE_URL="${APP_SERVICES_AUTH_SERVICE_URL:-http://localhost:8081}"
export APP_SERVICES_POST_SERVICE_URL="${APP_SERVICES_POST_SERVICE_URL:-http://localhost:8082}"
export APP_SERVICES_COMMENT_SERVICE_URL="${APP_SERVICES_COMMENT_SERVICE_URL:-http://localhost:8083}"
export APP_SERVICES_LIKE_SERVICE_URL="${APP_SERVICES_LIKE_SERVICE_URL:-http://localhost:8084}"
export APP_SERVICES_FOLLOW_SERVICE_URL="${APP_SERVICES_FOLLOW_SERVICE_URL:-http://localhost:8085}"
export APP_SERVICES_NOTIFICATION_SERVICE_URL="${APP_SERVICES_NOTIFICATION_SERVICE_URL:-http://localhost:8086}"
export APP_SERVICES_MEDIA_SERVICE_URL="${APP_SERVICES_MEDIA_SERVICE_URL:-http://localhost:8087}"
export APP_SERVICES_SEARCH_SERVICE_URL="${APP_SERVICES_SEARCH_SERVICE_URL:-http://localhost:8088}"
export APP_MEDIA_UPLOAD_DIR="${APP_MEDIA_UPLOAD_DIR:-/app/uploads/media}"

mkdir -p "${APP_MEDIA_UPLOAD_DIR}"

start_service eureka-server java -jar /app/services/eureka-server.jar
sleep "${EUREKA_STARTUP_DELAY_SECONDS:-12}"

start_service auth-service env \
  SPRING_DATASOURCE_URL="${AUTH_DB_URL:-$(mysql_url cs_auth_db)}" \
  SPRING_DATASOURCE_USERNAME="${AUTH_DB_USERNAME:-$MYSQL_USERNAME}" \
  SPRING_DATASOURCE_PASSWORD="${AUTH_DB_PASSWORD:-$MYSQL_PASSWORD}" \
  java -jar /app/services/auth-service.jar

start_service post-service env \
  SPRING_DATASOURCE_URL="${POST_DB_URL:-$(mysql_url cs_post_db)}" \
  SPRING_DATASOURCE_USERNAME="${POST_DB_USERNAME:-$MYSQL_USERNAME}" \
  SPRING_DATASOURCE_PASSWORD="${POST_DB_PASSWORD:-$MYSQL_PASSWORD}" \
  java -jar /app/services/post-service.jar

start_service comment-service env \
  SPRING_DATASOURCE_URL="${COMMENT_DB_URL:-$(mysql_url cs_comment_db)}" \
  SPRING_DATASOURCE_USERNAME="${COMMENT_DB_USERNAME:-$MYSQL_USERNAME}" \
  SPRING_DATASOURCE_PASSWORD="${COMMENT_DB_PASSWORD:-$MYSQL_PASSWORD}" \
  java -jar /app/services/comment-service.jar

start_service like-service env \
  SPRING_DATASOURCE_URL="${LIKE_DB_URL:-$(mysql_url cs_like_db)}" \
  SPRING_DATASOURCE_USERNAME="${LIKE_DB_USERNAME:-$MYSQL_USERNAME}" \
  SPRING_DATASOURCE_PASSWORD="${LIKE_DB_PASSWORD:-$MYSQL_PASSWORD}" \
  java -jar /app/services/like-service.jar

start_service follow-service env \
  SPRING_DATASOURCE_URL="${FOLLOW_DB_URL:-$(mysql_url cs_follow_db)}" \
  SPRING_DATASOURCE_USERNAME="${FOLLOW_DB_USERNAME:-$MYSQL_USERNAME}" \
  SPRING_DATASOURCE_PASSWORD="${FOLLOW_DB_PASSWORD:-$MYSQL_PASSWORD}" \
  java -jar /app/services/follow-service.jar

start_service notification-service env \
  SPRING_DATASOURCE_URL="${NOTIFICATION_DB_URL:-$(mysql_url cs_notification_db)}" \
  SPRING_DATASOURCE_USERNAME="${NOTIFICATION_DB_USERNAME:-$MYSQL_USERNAME}" \
  SPRING_DATASOURCE_PASSWORD="${NOTIFICATION_DB_PASSWORD:-$MYSQL_PASSWORD}" \
  java -jar /app/services/notification-service.jar

start_service media-service env \
  SPRING_DATASOURCE_URL="${MEDIA_DB_URL:-$(mysql_url cs_media_db)}" \
  SPRING_DATASOURCE_USERNAME="${MEDIA_DB_USERNAME:-$MYSQL_USERNAME}" \
  SPRING_DATASOURCE_PASSWORD="${MEDIA_DB_PASSWORD:-$MYSQL_PASSWORD}" \
  APP_MEDIA_UPLOAD_DIR="${APP_MEDIA_UPLOAD_DIR}" \
  java -jar /app/services/media-service.jar

start_service search-service env \
  SPRING_DATASOURCE_URL="${SEARCH_DB_URL:-$(mysql_url cs_search_db)}" \
  SPRING_DATASOURCE_USERNAME="${SEARCH_DB_USERNAME:-$MYSQL_USERNAME}" \
  SPRING_DATASOURCE_PASSWORD="${SEARCH_DB_PASSWORD:-$MYSQL_PASSWORD}" \
  java -jar /app/services/search-service.jar

sleep "${SERVICE_STARTUP_DELAY_SECONDS:-18}"

start_service api-gateway env \
  SERVER_PORT="${PORT:-8080}" \
  java -jar /app/services/api-gateway.jar

wait -n "${PIDS[@]}"
