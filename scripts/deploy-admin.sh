#!/bin/bash
set -euo pipefail

APP_NAME="flint-admin-api"
CONFIG_APP_NAME="${CONFIG_APP_NAME:-flint-api}"
DEPLOY_PATH="/home/ubuntu"
JAR_NAME="flint-admin-api-0.0.1-SNAPSHOT.jar"
NEW_JAR_PATH="/home/ubuntu/deploy/$JAR_NAME"
BACKUP_JAR="$DEPLOY_PATH/flint-admin-api-backup.jar"
DEPLOY_MODE="${DEPLOY_MODE:-docker}"
IMAGE_URI="${IMAGE_URI:-}"
PROFILE="${PROFILE:-dev}"
ADMIN_PORT="${ADMIN_PORT:-8081}"
REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-flint-$PROFILE-admin-redis}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
PARAMETER_BASE_PREFIX="/config/$CONFIG_APP_NAME"
PARAMETER_ENV_PREFIX="$PARAMETER_BASE_PREFIX/$PROFILE"

NGINX_CONF="/etc/nginx/conf.d/flint-admin-upstream.conf"
NGINX_PROXY_CONF="/etc/nginx/conf.d/flint-admin-api.conf"
HEALTH_CHECK_PATH="/actuator/health"
MAX_RETRY=12
RETRY_INTERVAL=5
LOG_LINES_ON_FAILURE=260
PREVIOUS_IMAGE=""
DEPLOY_LOG_FILE="$DEPLOY_PATH/logs/deploy-admin.log"

mkdir -p "$DEPLOY_PATH/logs"
touch "$DEPLOY_LOG_FILE"
exec > >(tee -a "$DEPLOY_LOG_FILE") 2>&1

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
    docker volume create flint-admin-redis-data >/dev/null
    docker run -d \
        --name "$REDIS_CONTAINER_NAME" \
        --restart unless-stopped \
        -p 127.0.0.1:6379:6379 \
        -v flint-admin-redis-data:/data \
        redis:7-alpine \
        redis-server --appendonly yes >/dev/null
}

ensure_nginx() {
    local default_location_conf="/etc/nginx/default.d/flint-admin-api.conf"
    local legacy_proxy_conf="$NGINX_PROXY_CONF"
    local proxy_conf
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

    temp_conf=$(mktemp)
    cat > "$temp_conf" <<EOF
upstream flint-admin-api {
    server 127.0.0.1:$ADMIN_PORT;
}
EOF
    run_as_root install -m 0644 "$temp_conf" "$NGINX_CONF"
    rm -f "$temp_conf"

    if [ -d /etc/nginx/default.d ] || grep -q "default.d" /etc/nginx/nginx.conf 2>/dev/null; then
        proxy_conf="$default_location_conf"
        run_as_root mkdir -p /etc/nginx/default.d
        run_as_root rm -f "$legacy_proxy_conf"
        if [ ! -f "$proxy_conf" ]; then
            temp_conf=$(mktemp)
            cat > "$temp_conf" <<'EOF'
location / {
    proxy_pass http://flint-admin-api;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 5s;
    proxy_read_timeout 120s;
}
EOF
            run_as_root install -m 0644 "$temp_conf" "$proxy_conf"
            rm -f "$temp_conf"
        fi
    else
        proxy_conf="$legacy_proxy_conf"
        if [ ! -f "$proxy_conf" ]; then
            temp_conf=$(mktemp)
            cat > "$temp_conf" <<'EOF'
server {
    listen 80;
    server_name _;

    client_max_body_size 20m;

    location / {
        proxy_pass http://flint-admin-api;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 120s;
    }
}
EOF
            run_as_root install -m 0644 "$temp_conf" "$proxy_conf"
            rm -f "$temp_conf"
        fi
    fi

    if [ -f "$NGINX_PROXY_CONF" ] && [ "$NGINX_PROXY_CONF" != "$proxy_conf" ]; then
        run_as_root rm -f "$NGINX_PROXY_CONF"
    fi

    if ! run_as_root nginx -t >/dev/null 2>&1; then
        log "ERROR: nginx configuration test failed during setup"
        run_as_root nginx -t || true
        exit 1
    fi

    run_as_root systemctl enable nginx
    run_as_root systemctl start nginx
    run_as_root nginx -s reload || true
}

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

container_name() {
    echo "$APP_NAME"
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

remember_previous_image() {
    local name
    name=$(container_name)
    PREVIOUS_IMAGE=$(docker inspect "$name" --format '{{.Config.Image}}' 2>/dev/null || true)
}

stop_admin_app() {
    local pid
    local name
    name=$(container_name)

    if [ "$DEPLOY_MODE" = "docker" ]; then
        if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
            log "Stopping container $name"
            docker stop -t 30 "$name" >/dev/null 2>&1 || true
            docker rm "$name" >/dev/null 2>&1 || true
        fi
    fi

    pid=$(lsof -ti:"$ADMIN_PORT" 2>/dev/null || true)
    if [ -n "$pid" ]; then
        log "Stopping admin application on port $ADMIN_PORT (PID: $pid)"
        kill -TERM "$pid" 2>/dev/null || true
        sleep 10
        if lsof -ti:"$ADMIN_PORT" > /dev/null 2>&1; then
            log "Force killing admin application on port $ADMIN_PORT"
            kill -9 "$(lsof -ti:"$ADMIN_PORT")" 2>/dev/null || true
        fi
    fi
}

start_docker_app() {
	local image="$1"
	local spring_config_import
	local name

	spring_config_import=$(build_spring_config_import) || return 1
	name=$(container_name)

	docker run -d \
		--name "$name" \
		--restart unless-stopped \
		--network host \
		"$image" \
        --spring.profiles.active="$PROFILE" \
        --spring.config.import="$spring_config_import" \
        --server.port="$ADMIN_PORT" >/dev/null

    log "Admin Docker application starting on port $ADMIN_PORT (container: $name)"
}

start_jar_app() {
    local spring_config_import

    cd "$DEPLOY_PATH" || { log "ERROR: Failed to cd to $DEPLOY_PATH"; return 1; }
    spring_config_import=$(build_spring_config_import) || return 1

    nohup java -jar "$JAR_NAME" \
        --spring.profiles.active="$PROFILE" \
        --spring.config.import="$spring_config_import" \
        --server.port="$ADMIN_PORT" \
        > "admin-$ADMIN_PORT.log" 2>&1 &

    log "Admin JAR application starting on port $ADMIN_PORT (PID: $!)"
}

print_log_tail() {
    local log_file="$DEPLOY_PATH/admin-$ADMIN_PORT.log"
    local name

    if [ "$DEPLOY_MODE" = "docker" ]; then
        name=$(container_name)
        if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
            log "Last $LOG_LINES_ON_FAILURE lines from container $name:"
            docker logs --tail "$LOG_LINES_ON_FAILURE" "$name" || true
        else
            log "Admin application container not found: $name"
        fi
    elif [ -f "$log_file" ]; then
        log "Last $LOG_LINES_ON_FAILURE lines from $log_file:"
        tail -n "$LOG_LINES_ON_FAILURE" "$log_file" || true
    else
        log "Admin log not found: $log_file"
    fi
}

health_check() {
    local health_response="/tmp/$APP_NAME-health-$ADMIN_PORT.out"
    local http_code
    log "Health checking admin on port $ADMIN_PORT..."

    for i in $(seq 1 "$MAX_RETRY"); do
        http_code=$(curl -sS \
            --connect-timeout 2 \
            --max-time 5 \
            -o "$health_response" \
            -w "%{http_code}" \
            "http://localhost:$ADMIN_PORT$HEALTH_CHECK_PATH" 2>/dev/null || true)

        if [[ "$http_code" =~ ^2 ]]; then
            log "Admin health check passed on port $ADMIN_PORT"
            rm -f "$health_response"
            return 0
        fi

        if [ -s "$health_response" ]; then
            log "Waiting for admin... ($i/$MAX_RETRY, HTTP $http_code, body: $(tr '\n' ' ' < "$health_response"))"
        else
            log "Waiting for admin... ($i/$MAX_RETRY, HTTP $http_code)"
        fi
        sleep "$RETRY_INTERVAL"
    done

    log "Admin health check failed on port $ADMIN_PORT"
    print_log_tail
    rm -f "$health_response"
    return 1
}

rollback() {
    log "Rolling back admin..."
    stop_admin_app

    if [ "$DEPLOY_MODE" = "docker" ] && [ -n "$PREVIOUS_IMAGE" ]; then
        log "Restoring previous admin image $PREVIOUS_IMAGE"
        start_docker_app "$PREVIOUS_IMAGE" || true
        return 0
    fi

    if [ "$DEPLOY_MODE" = "jar" ] && [ -f "$BACKUP_JAR" ]; then
        cp "$BACKUP_JAR" "$DEPLOY_PATH/$JAR_NAME"
        log "Restored admin JAR from backup"
        start_jar_app || true
    fi
}

deploy_docker() {
    login_to_ecr || return 1
    docker pull "$IMAGE_URI"
    remember_previous_image
    stop_admin_app
    start_docker_app "$IMAGE_URI"
}

deploy_jar() {
    if [ -f "$DEPLOY_PATH/$JAR_NAME" ]; then
        log "Backing up current admin JAR..."
        cp "$DEPLOY_PATH/$JAR_NAME" "$BACKUP_JAR"
    fi

    if [ -f "$NEW_JAR_PATH" ]; then
        log "Copying new admin JAR..."
        cp "$NEW_JAR_PATH" "$DEPLOY_PATH/$JAR_NAME"
    else
        log "ERROR: New admin JAR not found at $NEW_JAR_PATH"
        return 1
    fi

    stop_admin_app
    start_jar_app
}

deploy() {
    log "=== Admin Deployment Start ==="
    check_dependencies

    case "$DEPLOY_MODE" in
        docker)
            deploy_docker
            ;;
        jar)
            deploy_jar
            ;;
    esac

    if ! health_check; then
        log "Admin deployment failed!"
        rollback
        exit 1
    fi

    if [ "$DEPLOY_MODE" = "jar" ]; then
        rm -f "$NEW_JAR_PATH"
    else
        docker image prune -f >/dev/null 2>&1 || true
    fi

    log "=== Admin Deployment completed successfully ==="
}

deploy
