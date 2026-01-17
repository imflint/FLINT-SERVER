#!/bin/bash
set -euo pipefail

# 이미지 태그를 인자로 받음
IMAGE_TAG="${1:-ghcr.io/imflint/flint-api:latest}"

APP_NAME="flint-api"
DEPLOY_PATH="/home/ubuntu"
PROFILE="dev"

BLUE_PORT=8080
GREEN_PORT=8081
BLUE_CONTAINER="${APP_NAME}-blue"
GREEN_CONTAINER="${APP_NAME}-green"

NGINX_CONF="/etc/nginx/conf.d/flint-upstream.conf"
HEALTH_CHECK_PATH="/actuator/health"
MAX_RETRY=24
RETRY_INTERVAL=5

REGISTRY="ghcr.io"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

check_dependencies() {
    command -v docker >/dev/null 2>&1 || { log "ERROR: docker not installed"; exit 1; }
    command -v curl >/dev/null 2>&1 || { log "ERROR: curl not installed"; exit 1; }
}

# 현재 활성 포트 확인
get_active_port() {
    if [ -f "$DEPLOY_PATH/active_port" ]; then
        cat "$DEPLOY_PATH/active_port"
    else
        echo "$BLUE_PORT"
    fi
}

# 비활성 포트/컨테이너 반환
get_inactive_port() {
    local active_port
    active_port=$(get_active_port)
    if [ "$active_port" == "$BLUE_PORT" ]; then
        echo "$GREEN_PORT"
    else
        echo "$BLUE_PORT"
    fi
}

get_container_name() {
    local port="$1"
    if [ "$port" == "$BLUE_PORT" ]; then
        echo "$BLUE_CONTAINER"
    else
        echo "$GREEN_CONTAINER"
    fi
}

# GitHub Container Registry 로그인
docker_login() {
    log "Logging in to GitHub Container Registry..."

    # Parameter Store에서 토큰 가져오기 (기존 설정 경로와 동일한 패턴)
    local token
    token=$(aws ssm get-parameter \
        --name "/config/flint-api/ghcr.token" \
        --with-decryption \
        --query "Parameter.Value" \
        --output text 2>/dev/null) || true

    if [ -n "$token" ]; then
        echo "$token" | docker login "$REGISTRY" -u github --password-stdin
    else
        log "WARNING: No GHCR token found, assuming already logged in"
    fi
}

# 이미지 pull
pull_image() {
    log "Pulling image: $IMAGE_TAG"
    docker pull "$IMAGE_TAG"
}

# 컨테이너 종료 및 제거
stop_container() {
    local container_name="$1"
    if docker ps -a --format '{{.Names}}' | grep -q "^${container_name}$"; then
        log "Stopping container: $container_name"
        docker stop "$container_name" 2>/dev/null || true
        docker rm "$container_name" 2>/dev/null || true
    fi
}

# 컨테이너 시작
start_container() {
    local port="$1"
    local container_name
    container_name=$(get_container_name "$port")

    log "Starting container: $container_name on port $port"

    docker run -d \
        --name "$container_name" \
        --restart unless-stopped \
        -p "$port:8080" \
        -e "SPRING_PROFILES_ACTIVE=$PROFILE" \
        -e "SERVER_PORT=8080" \
        --memory=512m \
        --cpus=1 \
        "$IMAGE_TAG"

    log "Container $container_name started (port: $port)"
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
    local container_name
    container_name=$(get_container_name "$port")

    log "Rolling back..."
    stop_container "$container_name"
}

# 메인 배포 로직
deploy() {
    local active_port
    local inactive_port
    local inactive_container
    local active_container

    active_port=$(get_active_port)
    inactive_port=$(get_inactive_port)
    inactive_container=$(get_container_name "$inactive_port")
    active_container=$(get_container_name "$active_port")

    log "=== Blue-Green Deployment Start ==="
    log "Image: $IMAGE_TAG"
    log "Active port: $active_port ($active_container)"
    log "Deploying to: $inactive_port ($inactive_container)"

    # 0. 필수 명령어 확인
    check_dependencies

    # 1. Docker 로그인
    docker_login

    # 2. 이미지 pull
    pull_image

    # 3. 비활성 컨테이너 정리
    stop_container "$inactive_container"

    # 4. 새 컨테이너 시작
    if ! start_container "$inactive_port"; then
        log "Failed to start container"
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

    # 8. 이전 컨테이너 종료 (connection draining 대기)
    log "Waiting for connection draining (30s)..."
    sleep 30
    stop_container "$active_container"

    # 9. 오래된 이미지 정리
    log "Cleaning up old images..."
    docker image prune -f --filter "until=24h" || true

    log "=== Deployment completed successfully ==="
    log "New active port: $inactive_port ($inactive_container)"
}

# 실행
deploy
