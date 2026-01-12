#!/bin/bash

APP_NAME="flint-api"
DEPLOY_PATH="/home/ubuntu"
JAR_NAME="flint-api-0.0.1-SNAPSHOT.jar"
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
    local active_port=$(get_active_port)
    if [ "$active_port" == "$BLUE_PORT" ]; then
        echo "$GREEN_PORT"
    else
        echo "$BLUE_PORT"
    fi
}

# 특정 포트의 프로세스 종료
kill_app_on_port() {
    local port=$1
    local pid=$(lsof -ti:$port)
    if [ -n "$pid" ]; then
        log "Stopping application on port $port (PID: $pid)"
        kill $pid
        sleep 2
        # 강제 종료
        if lsof -ti:$port > /dev/null 2>&1; then
            kill -9 $(lsof -ti:$port)
        fi
    fi
}

# 앱 시작
start_app() {
    local port=$1
    log "Starting application on port $port"

    cd $DEPLOY_PATH
    nohup java -jar $JAR_NAME \
        --spring.profiles.active=$PROFILE \
        --server.port=$port \
        > app-$port.log 2>&1 &

    log "Application starting on port $port (PID: $!)"
}

# 헬스체크
health_check() {
    local port=$1
    log "Health checking on port $port..."

    for i in $(seq 1 $MAX_RETRY); do
        if curl -sf http://localhost:$port$HEALTH_CHECK_PATH > /dev/null 2>&1; then
            log "Health check passed on port $port"
            return 0
        fi
        log "Waiting for application... ($i/$MAX_RETRY)"
        sleep $RETRY_INTERVAL
    done

    log "Health check failed on port $port"
    return 1
}

# nginx upstream 전환
switch_nginx() {
    local new_port=$1
    log "Switching nginx to port $new_port"

    # upstream 설정 변경
    sudo tee $NGINX_CONF > /dev/null << EOF
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

# 메인 배포 로직
deploy() {
    local active_port=$(get_active_port)
    local inactive_port=$(get_inactive_port)

    log "=== Blue-Green Deployment Start ==="
    log "Active port: $active_port, Deploying to: $inactive_port"

    # 1. 비활성 포트의 기존 프로세스 종료
    kill_app_on_port $inactive_port

    # 2. 새 버전 시작
    start_app $inactive_port

    # 3. 헬스체크
    if ! health_check $inactive_port; then
        log "Deployment failed! Rolling back..."
        kill_app_on_port $inactive_port
        exit 1
    fi

    # 4. nginx 전환
    if ! switch_nginx $inactive_port; then
        log "Nginx switch failed! Rolling back..."
        kill_app_on_port $inactive_port
        exit 1
    fi

    # 5. 활성 포트 기록
    echo $inactive_port > "$DEPLOY_PATH/active_port"

    # 6. 이전 버전 종료 (잠시 대기 후)
    sleep 5
    kill_app_on_port $active_port

    log "=== Deployment completed successfully ==="
    log "New active port: $inactive_port"
}

# 실행
deploy
