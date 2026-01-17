# Docker 기반 배포 마이그레이션 계획

## 개요

현재 JAR 기반 배포에서 Docker 기반 배포로 전환하는 계획입니다.

---

## 1. bootBuildImage 설정

`apps/api/build.gradle`에 추가:

```gradle
bootBuildImage {
    def ecrRegistry = System.getenv('ECR_REGISTRY') ?: 'flint-api'
    def imageTag = System.getenv('IMAGE_TAG') ?: 'latest'

    imageName = "${ecrRegistry}:${imageTag}"

    environment = [
        "BP_JVM_VERSION": "21"
    ]
}
```

---

## 2. ECR 설정

### ECR 레포지토리 생성

```bash
aws ecr create-repository \
    --repository-name flint-api \
    --region ap-northeast-2
```

### IAM 권한 추가

EC2 IAM Role에 ECR 접근 권한 추가:

```json
{
  "Effect": "Allow",
  "Action": [
    "ecr:GetAuthorizationToken",
    "ecr:BatchCheckLayerAvailability",
    "ecr:GetDownloadUrlForLayer",
    "ecr:BatchGetImage"
  ],
  "Resource": "*"
}
```

---

## 3. EC2에 Docker 설치

```bash
sudo apt update
sudo apt install docker.io -y
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ubuntu
```

---

## 4. GitHub Actions 수정

```yaml
jobs:
  build:
    steps:
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ap-northeast-2

      - name: Login to ECR
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build and push image
        run: |
          ./gradlew :apps:api:bootBuildImage
        env:
          ECR_REGISTRY: ${{ secrets.ECR_REGISTRY }}
          IMAGE_TAG: ${{ github.sha }}

  deploy:
    steps:
      - name: Deploy to EC2
        uses: appleboy/ssh-action@v1.2.4
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USERNAME }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin ${{ secrets.ECR_REGISTRY }}
            /home/ubuntu/deploy-docker.sh ${{ secrets.ECR_REGISTRY }}:${{ github.sha }}
```

---

## 5. deploy-docker.sh (Blue-Green Docker 버전)

```bash
#!/bin/bash

IMAGE=$1
BLUE_PORT=8080
GREEN_PORT=8081

# 현재 활성 포트 확인
get_active_port() {
    cat /home/ubuntu/active_port 2>/dev/null || echo "$BLUE_PORT"
}

get_inactive_port() {
    local active=$(get_active_port)
    [ "$active" == "$BLUE_PORT" ] && echo "$GREEN_PORT" || echo "$BLUE_PORT"
}

active_port=$(get_active_port)
inactive_port=$(get_inactive_port)

# 새 컨테이너 시작
docker pull $IMAGE
docker stop flint-$inactive_port 2>/dev/null || true
docker rm flint-$inactive_port 2>/dev/null || true
docker run -d --name flint-$inactive_port \
    -p $inactive_port:8080 \
    -e SPRING_PROFILES_ACTIVE=dev \
    $IMAGE

# 헬스체크
for i in {1..12}; do
    if curl -sf http://localhost:$inactive_port/actuator/health; then
        break
    fi
    sleep 5
done

# nginx 전환
sudo tee /etc/nginx/conf.d/flint-upstream.conf << EOF
upstream flint-api {
    server 127.0.0.1:$inactive_port;
}
EOF
sudo nginx -s reload

# 활성 포트 기록
echo $inactive_port > /home/ubuntu/active_port

# 이전 컨테이너 정리 (30초 후)
sleep 30
docker stop flint-$active_port 2>/dev/null || true
docker rm flint-$active_port 2>/dev/null || true
```

---

## 6. GitHub Secrets 추가

| Secret | 설명 |
|--------|------|
| `AWS_ACCESS_KEY_ID` | ECR 접근용 |
| `AWS_SECRET_ACCESS_KEY` | ECR 접근용 |
| `ECR_REGISTRY` | 예: `123456789.dkr.ecr.ap-northeast-2.amazonaws.com/flint-api` |

---

## 7. 롤백

```bash
# 이전 이미지로 롤백
docker run -d --name flint-8080 -p 8080:8080 $ECR_REGISTRY:이전태그
```

태그만 변경하면 되므로 JAR 방식보다 롤백이 간편합니다.
