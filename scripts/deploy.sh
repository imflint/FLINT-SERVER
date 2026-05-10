#!/bin/bash
set -euo pipefail

APP_NAME="flint-api"
DEPLOY_PATH="/home/ubuntu"
JAR_NAME="flint-api-0.0.1-SNAPSHOT.jar"
NEW_JAR_PATH="/home/ubuntu/deploy/$JAR_NAME"
BACKUP_JAR="$DEPLOY_PATH/flint-api-backup.jar"
DEPLOY_MODE="${DEPLOY_MODE:-docker}"
IMAGE_URI="${IMAGE_URI:-}"
PROFILE="dev"
REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-flint-$PROFILE-redis}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
PARAMETER_BASE_PREFIX="/config/$APP_NAME"
PARAMETER_ENV_PREFIX="$PARAMETER_BASE_PREFIX/$PROFILE"

BLUE_PORT=8080
GREEN_PORT=8081

NGINX_CONF="/etc/nginx/conf.d/flint-upstream.conf"
HEALTH_CHECK_PATH="/actuator/health"
MAX_RETRY=12
RETRY_INTERVAL=5
LOG_LINES_ON_FAILURE=120

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

run_as_root() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    else
        sudo "$@"
    fi
}

ensure_docker() {
    if command -v docker >/dev/null 2>&1; then
        return 0
    fi

    log "Docker not found; installing Docker"
    if command -v dnf >/dev/null 2>&1; then
        run_as_root dnf install -y docker
    elif command -v yum >/dev/null 2>&1; then
        run_as_root yum install -y docker
    elif command -v apt-get >/dev/null 2>&1; then
        run_as_root apt-get update
        run_as_root apt-get install -y docker.io
    else
        log "ERROR: supported package manager not found for Docker installation"
        exit 1
    fi

    run_as_root systemctl enable docker
    run_as_root systemctl start docker
}

ensure_redis() {
    if docker ps --format '{{.Names}}' | grep -qx "$REDIS_CONTAINER_NAME"; then
        return 0
    fi

    if docker ps -a --format '{{.Names}}' | grep -qx "$REDIS_CONTAINER_NAME"; then
        log "Starting Redis container $REDIS_CONTAINER_NAME"
        docker start "$REDIS_CONTAINER_NAME" >/dev/null
        return 0
    fi

    log "Creating Redis container $REDIS_CONTAINER_NAME"
    docker volume create flint-redis-data >/dev/null
    docker run -d \
        --name "$REDIS_CONTAINER_NAME" \
        --restart unless-stopped \
        -p 127.0.0.1:6379:6379 \
        -v flint-redis-data:/data \
        redis:7-alpine \
        redis-server --appendonly yes >/dev/null
}

ensure_nginx() {
    local proxy_conf="/etc/nginx/conf.d/flint-api.conf"
    local temp_conf

    if ! command -v nginx >/dev/null 2>&1; then
        log "Nginx not found; installing nginx"
        if command -v dnf >/dev/null 2>&1; then
            run_as_root dnf install -y nginx
        elif command -v yum >/dev/null 2>&1; then
            run_as_root yum install -y nginx
        elif command -v apt-get >/dev/null 2>&1; then
            run_as_root apt-get update
            run_as_root apt-get install -y nginx
        else
            log "ERROR: supported package manager not found for nginx installation"
            exit 1
        fi
    fi

    run_as_root mkdir -p /etc/nginx/conf.d

    if [ ! -f "$proxy_conf" ]; then
        log "Creating nginx proxy config $proxy_conf"
        temp_conf=$(mktemp)
        cat > "$temp_conf" <<'EOF'
server {
    listen 80 default_server;
    server_name _;

    client_max_body_size 20m;

    location / {
        proxy_pass http://flint-api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }
}
EOF
        run_as_root install -m 0644 "$temp_conf" "$proxy_conf"
        rm -f "$temp_conf"
    fi

    run_as_root systemctl enable nginx
    run_as_root systemctl start nginx
}

# 필수 명령어 확인
check_dependencies() {
    command -v lsof >/dev/null 2>&1 || { log "ERROR: lsof not installed"; exit 1; }
    command -v curl >/dev/null 2>&1 || { log "ERROR: curl not installed"; exit 1; }
    command -v aws >/dev/null 2>&1 || { log "ERROR: aws cli not installed"; exit 1; }

    case "$DEPLOY_MODE" in
        docker)
            ensure_docker
            ensure_redis
            ;;
        jar)
            command -v java >/dev/null 2>&1 || { log "ERROR: java not installed"; exit 1; }
            ;;
        *)
            log "ERROR: Unsupported DEPLOY_MODE=$DEPLOY_MODE"
            exit 1
            ;;
    esac

    ensure_nginx
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

    if [ "$DEPLOY_MODE" = "docker" ]; then
        local container_name
        container_name=$(container_name_for_port "$port")
        if docker ps -a --format '{{.Names}}' | grep -qx "$container_name"; then
            log "Stopping container $container_name"
            docker stop -t 30 "$container_name" >/dev/null 2>&1 || true
            docker rm "$container_name" >/dev/null 2>&1 || true
        fi
    fi

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

container_name_for_port() {
    local port="$1"
    echo "$APP_NAME-$port"
}

get_database_secret_arn() {
    local secret_arn
    local error_file
    local error_message

    error_file=$(mktemp)
    if secret_arn=$(aws ssm get-parameter \
        --region "$AWS_REGION" \
        --name "$PARAMETER_ENV_PREFIX/database.secret-arn" \
        --query "Parameter.Value" \
        --output text 2>"$error_file"); then
        rm -f "$error_file"
        if [ "$secret_arn" != "None" ]; then
            echo "$secret_arn"
        fi
        return 0
    fi

    error_message=$(tr '\n' ' ' < "$error_file")
    rm -f "$error_file"

    if [[ "$error_message" == *"ParameterNotFound"* ]]; then
        log "Optional $PARAMETER_ENV_PREFIX/database.secret-arn not found; using Parameter Store database.password if configured" >&2
        return 0
    fi

    log "ERROR: Failed to read $PARAMETER_ENV_PREFIX/database.secret-arn: $error_message" >&2
    return 1
}

build_spring_config_import() {
    local database_secret_arn
    local spring_config_import

    spring_config_import="aws-parameterstore:$PARAMETER_BASE_PREFIX/,aws-parameterstore:$PARAMETER_ENV_PREFIX/"

    if ! database_secret_arn=$(get_database_secret_arn); then
        return 1
    fi

    if [ -n "$database_secret_arn" ]; then
        log "Using RDS managed database credentials from Secrets Manager" >&2
        spring_config_import="$spring_config_import,aws-secretsmanager:$database_secret_arn?prefix=database."
    else
        log "Using database credentials from Parameter Store" >&2
    fi

    echo "$spring_config_import"
}

login_to_ecr() {
    local registry

    if [ -z "$IMAGE_URI" ]; then
        log "ERROR: IMAGE_URI is required for docker deployment"
        return 1
    fi

    registry="${IMAGE_URI%%/*}"
    log "Logging in to ECR registry $registry"
    aws ecr get-login-password --region "$AWS_REGION" \
        | docker login --username AWS --password-stdin "$registry" >/dev/null
}

start_docker_app() {
    local port="$1"
    local spring_config_import
    local container_name

    log "Starting Docker application on port $port"

    spring_config_import=$(build_spring_config_import) || return 1
    container_name=$(container_name_for_port "$port")

    login_to_ecr || return 1
    docker pull "$IMAGE_URI"
    docker rm -f "$container_name" >/dev/null 2>&1 || true

    docker run -d \
        --name "$container_name" \
        --restart unless-stopped \
        --network host \
        "$IMAGE_URI" \
        --spring.profiles.active="$PROFILE" \
        --spring.config.import="$spring_config_import" \
        --server.port="$port" >/dev/null

    log "Docker application starting on port $port (container: $container_name)"
}

start_jar_app() {
    local port="$1"
    local spring_config_import

    log "Starting application on port $port"

    cd "$DEPLOY_PATH" || { log "ERROR: Failed to cd to $DEPLOY_PATH"; return 1; }
    spring_config_import=$(build_spring_config_import) || return 1

    nohup java -jar "$JAR_NAME" \
        --spring.profiles.active="$PROFILE" \
        --spring.config.import="$spring_config_import" \
        --server.port="$port" \
        > "app-$port.log" 2>&1 &

    log "Application starting on port $port (PID: $!)"
}

start_app() {
    local port="$1"

    if [ "$DEPLOY_MODE" = "docker" ]; then
        start_docker_app "$port"
    else
        start_jar_app "$port"
    fi
}

print_app_log_tail() {
    local port="$1"
    local log_file="$DEPLOY_PATH/app-$port.log"
    local container_name

    if [ "$DEPLOY_MODE" = "docker" ]; then
        container_name=$(container_name_for_port "$port")
        if docker ps -a --format '{{.Names}}' | grep -qx "$container_name"; then
            log "Last $LOG_LINES_ON_FAILURE lines from container $container_name:"
            docker logs --tail "$LOG_LINES_ON_FAILURE" "$container_name" || true
        else
            log "Application container not found: $container_name"
        fi
    elif [ -f "$log_file" ]; then
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
    local temp_conf
    log "Switching nginx to port $new_port"

    temp_conf=$(mktemp)
    cat > "$temp_conf" << EOF
upstream flint-api {
    server 127.0.0.1:$new_port;
}
EOF
    run_as_root install -m 0644 "$temp_conf" "$NGINX_CONF"
    rm -f "$temp_conf"

    if run_as_root nginx -t > /dev/null 2>&1; then
        run_as_root nginx -s reload
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
    if [ "$DEPLOY_MODE" = "jar" ] && [ -f "$BACKUP_JAR" ]; then
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

    if [ "$DEPLOY_MODE" = "jar" ]; then
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
    else
        log "Docker image: $IMAGE_URI"
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
    if [ "$DEPLOY_MODE" = "jar" ]; then
        rm -f "$NEW_JAR_PATH"
    else
        docker image prune -f >/dev/null 2>&1 || true
    fi

    log "=== Deployment completed successfully ==="
    log "New active port: $inactive_port"
}

# 실행
deploy
