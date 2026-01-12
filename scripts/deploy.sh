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

NGINX_CONF="/etc/nginx/conf.d/flint-upstream.conf"
HEALTH_CHECK_PATH="/actuator/health"
MAX_RETRY=12
RETRY_INTERVAL=5

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

# 헬스체크
health_check() {
    local port="$1"
    log "Health checking on port $port..."

    for i in $(seq 1 "$MAX_RETRY"); do
        if curl -sf "http://localhost:$port$HEALTH_CHECK_PATH" > /dev/null 2>&1; then
            log "Health check passed on port $port"
            return 0
        fi
        log "Waiting for application... ($i/$MAX_RETRY)"
        sleep "$RETRY_INTERVAL"
    done

    log "Health check failed on port $port"
    return 1
}

# nginx upstream 전환
switch_nginx() {
    local new_port="$1"
    log "Switching nginx to port $new_port"

    # upstream 설정 변경
    sudo tee "$NGINX_CONF" > /dev/null << EOF
upstream flint-api {
    server 127.0.0.1:$new_port;
}
EOF

    # nginx 설정 테스트 및 리로드
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

# 실행
deploy
