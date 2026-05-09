#!/bin/bash
set -euo pipefail

APP_NAME="flint-api"
DEPLOY_PATH="/home/ubuntu"
JAR_NAME="flint-api-0.0.1-SNAPSHOT.jar"
NEW_JAR_PATH="/home/ubuntu/deploy/$JAR_NAME"
BACKUP_JAR="$DEPLOY_PATH/flint-api-backup.jar"
PROFILE="dev"

BLUE_PORT=8080
GREEN_PORT=8081

# Batch (단일 인스턴스, blue-green 미적용 — 사용자 트래픽 없음)
BATCH_JAR_NAME="flint-batch-0.0.1-SNAPSHOT.jar"
NEW_BATCH_JAR_PATH="/home/ubuntu/deploy/$BATCH_JAR_NAME"
BATCH_BACKUP_JAR="$DEPLOY_PATH/flint-batch-backup.jar"
BATCH_PORT=8082

NGINX_CONF="/etc/nginx/conf.d/flint-upstream.conf"
HEALTH_CHECK_PATH="/actuator/health"
MAX_RETRY=12
RETRY_INTERVAL=5
LOG_LINES_ON_FAILURE=120

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# 필수 명령어 확인
check_dependencies() {
    command -v lsof >/dev/null 2>&1 || { log "ERROR: lsof not installed"; exit 1; }
    command -v curl >/dev/null 2>&1 || { log "ERROR: curl not installed"; exit 1; }
    command -v java >/dev/null 2>&1 || { log "ERROR: java not installed"; exit 1; }
}

# 현재 활성 포트 확인
get_active_port() {
    if [ -f "$DEPLOY_PATH/active_port" ]; then
        cat "$DEPLOY_PATH/active_port"
    else
        echo "$BLUE_PORT"
    fi
}

# 비활성 포트 반환
get_inactive_port() {
    local active_port
    active_port=$(get_active_port)
    if [ "$active_port" == "$BLUE_PORT" ]; then
        echo "$GREEN_PORT"
    else
        echo "$BLUE_PORT"
    fi
}

# 특정 포트의 프로세스 종료
kill_app_on_port() {
    local port="$1"
    local pid
    pid=$(lsof -ti:"$port" 2>/dev/null || true)
    if [ -n "$pid" ]; then
        log "Stopping application on port $port (PID: $pid)"
        kill -TERM "$pid" 2>/dev/null || true
        # 그레이스풀 셧다운 대기 (Spring Boot 기본 30초)
        sleep 10
        # 강제 종료
        if lsof -ti:"$port" > /dev/null 2>&1; then
            log "Force killing application on port $port"
            kill -9 "$(lsof -ti:"$port")" 2>/dev/null || true
        fi
    fi
}

# 앱 시작
start_app() {
    local port="$1"
    log "Starting application on port $port"

    cd "$DEPLOY_PATH" || { log "ERROR: Failed to cd to $DEPLOY_PATH"; return 1; }
    nohup java -jar "$JAR_NAME" \
        --spring.profiles.active="$PROFILE" \
        --server.port="$port" \
        > "app-$port.log" 2>&1 &

    log "Application starting on port $port (PID: $!)"
}

print_app_log_tail() {
    local port="$1"
    local log_file="$DEPLOY_PATH/app-$port.log"

    if [ -f "$log_file" ]; then
        log "Last $LOG_LINES_ON_FAILURE lines from $log_file:"
        tail -n "$LOG_LINES_ON_FAILURE" "$log_file" || true
    else
        log "Application log not found: $log_file"
    fi
}

# 헬스체크
health_check() {
    local port="$1"
    local health_response="/tmp/$APP_NAME-health-$port.out"
    local http_code
    log "Health checking on port $port..."

    for i in $(seq 1 "$MAX_RETRY"); do
        http_code=$(curl -sS \
            --connect-timeout 2 \
            --max-time 5 \
            -o "$health_response" \
            -w "%{http_code}" \
            "http://localhost:$port$HEALTH_CHECK_PATH" 2>/dev/null || true)

        if [[ "$http_code" =~ ^2 ]]; then
            log "Health check passed on port $port"
            rm -f "$health_response"
            return 0
        fi

        if [ -s "$health_response" ]; then
            log "Waiting for application... ($i/$MAX_RETRY, HTTP $http_code, body: $(tr '\n' ' ' < "$health_response"))"
        else
            log "Waiting for application... ($i/$MAX_RETRY, HTTP $http_code)"
        fi
        sleep "$RETRY_INTERVAL"
    done

    log "Health check failed on port $port"
    print_app_log_tail "$port"
    rm -f "$health_response"
    return 1
}

# nginx upstream 전환
switch_nginx() {
    local new_port="$1"
    log "Switching nginx to port $new_port"

    sudo tee "$NGINX_CONF" > /dev/null << EOF
upstream flint-api {
    server 127.0.0.1:$new_port;
}
EOF

    if sudo nginx -t > /dev/null 2>&1; then
        sudo nginx -s reload
        log "Nginx switched to port $new_port"
        return 0
    else
        log "Nginx configuration test failed"
        return 1
    fi
}

# 롤백
rollback() {
    local port="$1"
    log "Rolling back..."
    kill_app_on_port "$port"
    if [ -f "$BACKUP_JAR" ]; then
        cp "$BACKUP_JAR" "$DEPLOY_PATH/$JAR_NAME"
        log "Restored JAR from backup"
    fi
}

# 메인 배포 로직
deploy() {
    local active_port
    local inactive_port
    active_port=$(get_active_port)
    inactive_port=$(get_inactive_port)

    log "=== Blue-Green Deployment Start ==="
    log "Active port: $active_port, Deploying to: $inactive_port"

    # 0. 필수 명령어 확인
    check_dependencies

    # 1. 기존 JAR 백업
    if [ -f "$DEPLOY_PATH/$JAR_NAME" ]; then
        log "Backing up current JAR..."
        cp "$DEPLOY_PATH/$JAR_NAME" "$BACKUP_JAR"
    fi

    # 2. 새 JAR 복사
    if [ -f "$NEW_JAR_PATH" ]; then
        log "Copying new JAR..."
        cp "$NEW_JAR_PATH" "$DEPLOY_PATH/$JAR_NAME"
    else
        log "ERROR: New JAR not found at $NEW_JAR_PATH"
        exit 1
    fi

    # 3. 비활성 포트의 기존 프로세스 종료
    kill_app_on_port "$inactive_port"

    # 4. 새 버전 시작
    if ! start_app "$inactive_port"; then
        log "Failed to start application"
        rollback "$inactive_port"
        exit 1
    fi

    # 5. 헬스체크
    if ! health_check "$inactive_port"; then
        log "Deployment failed!"
        rollback "$inactive_port"
        exit 1
    fi

    # 6. nginx 전환
    if ! switch_nginx "$inactive_port"; then
        log "Nginx switch failed!"
        rollback "$inactive_port"
        exit 1
    fi

    # 7. 활성 포트 기록
    echo "$inactive_port" > "$DEPLOY_PATH/active_port"

    # 8. 이전 버전 종료 (connection draining 대기)
    log "Waiting for connection draining (30s)..."
    sleep 30
    kill_app_on_port "$active_port"

    # 9. deploy 폴더 정리
    rm -f "$NEW_JAR_PATH"

    log "=== Deployment completed successfully ==="
    log "New active port: $inactive_port"
}

# Batch 앱 시작 (단일 인스턴스, log: batch-<port>.log)
start_batch_app() {
    local port="$1"
    log "Starting batch application on port $port"

    cd "$DEPLOY_PATH" || { log "ERROR: Failed to cd to $DEPLOY_PATH"; return 1; }
    nohup java -jar "$BATCH_JAR_NAME" \
        --spring.profiles.active="$PROFILE" \
        --server.port="$port" \
        > "batch-$port.log" 2>&1 &

    log "Batch starting on port $port (PID: $!)"
}

# Batch 롤백: 새 배포 실패 시 백업 jar로 복구 후 재기동
rollback_batch() {
    log "Rolling back batch..."
    kill_app_on_port "$BATCH_PORT"
    if [ -f "$BATCH_BACKUP_JAR" ]; then
        cp "$BATCH_BACKUP_JAR" "$DEPLOY_PATH/$BATCH_JAR_NAME"
        log "Restored batch JAR from backup"
        start_batch_app "$BATCH_PORT" || true
    fi
}

# Batch 배포: kill → 새 jar 시작 → health check. blue-green 없음(다운타임 허용).
deploy_batch() {
    if [ ! -f "$NEW_BATCH_JAR_PATH" ]; then
        log "No new batch JAR at $NEW_BATCH_JAR_PATH — skipping batch deploy"
        return 0
    fi

    log "=== Batch Deployment Start ==="

    if [ -f "$DEPLOY_PATH/$BATCH_JAR_NAME" ]; then
        log "Backing up current batch JAR..."
        cp "$DEPLOY_PATH/$BATCH_JAR_NAME" "$BATCH_BACKUP_JAR"
    fi

    log "Copying new batch JAR..."
    cp "$NEW_BATCH_JAR_PATH" "$DEPLOY_PATH/$BATCH_JAR_NAME"

    kill_app_on_port "$BATCH_PORT"

    if ! start_batch_app "$BATCH_PORT"; then
        log "Failed to start batch application"
        rollback_batch
        return 1
    fi

    if ! health_check "$BATCH_PORT"; then
        log "Batch health check failed!"
        rollback_batch
        return 1
    fi

    rm -f "$NEW_BATCH_JAR_PATH"
    log "=== Batch Deployment completed successfully ==="
    return 0
}

# 실행 — api(blue-green) 우선, 그 후 batch. batch 실패는 전체 배포를 실패시키지 않음.
deploy
if ! deploy_batch; then
    log "WARNING: Batch deploy failed but API deploy succeeded. Investigate batch logs."
fi
