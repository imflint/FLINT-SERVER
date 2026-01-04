# Flint API Server

콘텐츠 큐레이션 플랫폼 Flint의 백엔드 API 서버입니다.

## 기술 스택

- **Java 21** + **Spring Boot 3.5**
- **Gradle** (멀티 모듈)
- **MySQL 8.0** + **JPA/Hibernate**
- **Redis** (캐싱)
- **QueryDSL** (동적 쿼리)
- **P6Spy** (SQL 로깅)

## 프로젝트 구조

```
flint-api/
├── apps/                       # 애플리케이션
│   └── api/                    # REST API 애플리케이션
│       └── config/             # Security, Swagger, JPA 설정
│       └── controller/         # API 엔드포인트
│       └── facade/             # HomeQueryFacade, DiscoveryQueryFacade
│
├── modules/                    # 도메인 모듈
│   ├── shared/                 # 공통 컴포넌트
│   ├── user/                   # 사용자 도메인
│   ├── auth/                   # 인증 도메인
│   ├── content/                # 콘텐츠 도메인
│   ├── collection/             # 컬렉션 도메인
│   ├── bookmark/               # 북마크 도메인
│   ├── taste/                  # 취향 키워드 도메인
│   └── search/                 # 검색 도메인
│
├── infra/                      # 외부 인프라 모듈
│   └── redis/
│
├── .env                        # 환경 변수 (git ignored)
└── PRD.md                      # 제품 요구사항 문서
```

## 모듈 상세

### apps:api
REST API 애플리케이션 모듈. 모든 도메인 모듈을 조합하여 API를 제공합니다.

| 패키지 | 역할 |
|--------|------|
| `controller` | REST 엔드포인트 |
| `facade` | 여러 모듈을 조합하는 쿼리 파사드 (Home, Discovery) |
| `config` | Spring Security, Swagger, JPA Auditing 설정 |

### modules:shared
모든 모듈에서 공유하는 공통 컴포넌트입니다.

| 패키지 | 역할 |
|--------|------|
| `domain` | Base, BaseTime 등 JPA 공통 엔티티 |
| `dto` | PaginationResponse, PaginationMeta, SliceCursor |
| `exception` | GeneralException, ErrorCode, ProblemDetail (RFC 9457) |
| `util` | QueryDslUtil, SoftDeleteUtil |
| `p6spy` | SQL 로깅 포맷터 |

### modules:user
사용자 도메인을 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `User` | 사용자 정보 (닉네임, 프로필 이미지, 소개) |

### modules:auth
인증/인가를 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `Credential` | 소셜 로그인 자격 증명 (provider, providerId) |
| `RefreshToken` | JWT 리프레시 토큰 |

### modules:content
콘텐츠 도메인을 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `Content` | 사용자가 저장한 콘텐츠 (URL, 제목, 메모) |

### modules:collection
컬렉션 도메인을 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `Collection` | 콘텐츠를 묶는 컬렉션 (제목, 설명, 공개 여부) |
| `CollectionContent` | 컬렉션-콘텐츠 연결 |

### modules:bookmark
북마크 및 최근 조회 기록을 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `CollectionBookmark` | 컬렉션 북마크 |
| `RecentViewedCollection` | 최근 조회한 컬렉션 |

### modules:taste
취향 키워드 도메인을 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `Keyword` | 취향 키워드 마스터 |
| `UserKeyword` | 사용자-키워드 연결 |

### modules:search
검색 기능을 담당합니다.

### infra:redis
Redis 캐싱 인프라를 담당합니다.

| 패키지 | 역할 |
|--------|------|
| `config` | RedisTemplate, CacheManager 설정 |

## 모듈 의존성 규칙

```
apps:api
   ├── modules:user
   ├── modules:auth
   ├── modules:content
   ├── modules:collection
   ├── modules:bookmark
   ├── modules:taste
   ├── modules:search
   │      │
   │      └── modules:shared (모든 도메인 모듈이 의존)
   │
   └── infra:redis
```

- `apps:*` → `modules:*`, `infra:*` 의존
- `modules:*` → `modules:shared`만 의존 (다른 도메인 모듈 의존 금지)
- `infra:*` → 외부 라이브러리만 의존 (도메인 모듈 의존 금지)
- 엔티티는 단일 모듈 소유, 다른 모듈은 ID로만 참조
- 모듈 간 순환 의존 금지

## Convention

### 레이어드 아키텍처

각 도메인 모듈은 다음 구조를 따릅니다:

```
modules/{domain}/
├── domain/         # 엔티티, VO
├── repository/     # JPA Repository
├── service/        # 비즈니스 로직
├── dto/            # 요청/응답 DTO
└── exception/      # 도메인 예외
```

### 예외 처리 (RFC 9457)

```java
// 도메인 예외 정의
public enum UserErrorCode implements AppError {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 없음", "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
}

// 예외 발생
throw new GeneralException(UserErrorCode.USER_NOT_FOUND);
```

### 페이지네이션

```java
// Offset 기반
PaginationResponse.ofOffset(page);

// Cursor 기반
PaginationResponse.ofCursor(sliceCursor);
```
