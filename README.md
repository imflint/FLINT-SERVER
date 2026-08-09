# Flint API Server

콘텐츠 큐레이션 플랫폼 Flint의 백엔드 API 서버입니다.

## 기술 스택

- **Java 21** + **Spring Boot 3.5**
- **Gradle** (멀티 모듈)
- **MySQL 8.0** + **JPA/Hibernate**
- **Redis** (캐싱, Refresh Token 저장)
- **QueryDSL** (동적 쿼리)
- **P6Spy** (SQL 로깅)

## 프로젝트 구조

```
flint-api/
├── apps/                       # 애플리케이션
│   └── api/                    # REST API 애플리케이션
│       ├── domain/             # 도메인별 컨트롤러, DTO, 파사드
│       │   ├── auth/
│       │   ├── bookmark/
│       │   ├── collection/
│       │   ├── discovery/
│       │   ├── home/
│       │   └── user/
│       └── global/             # 공통 설정 및 인프라
│           ├── config/         # Security, Swagger, WebMvc 설정
│           ├── exception/      # GlobalExceptionHandler
│           ├── oauth/          # OAuth 클라이언트 (Kakao)
│           ├── security/       # JWT 필터
│           └── storage/        # S3 Presigned URL
│
├── modules/                    # 도메인 모듈
│   ├── shared/                 # 공통 컴포넌트
│   ├── user/                   # 사용자 도메인
│   ├── auth/                   # 인증 도메인
│   ├── content/                # 콘텐츠 도메인
│   ├── collection/             # 컬렉션 도메인
│   ├── bookmark/               # 북마크 도메인
│   ├── taste/                  # 취향 키워드 도메인
│   ├── ott/                    # OTT 플랫폼 도메인
│   └── search/                 # 검색 도메인
│
├── infra/                      # 외부 인프라 모듈
│   ├── redis/                  # Redis 연동
│   └── s3/                     # AWS S3 연동
│
└── .env                        # 환경 변수 (git ignored)
```

## 아키텍처

### Modular Monolith

도메인별 모듈이 독립적으로 분리된 **모듈러 모놀리식** 구조를 채택. Gradle 멀티 모듈로 빌드하여 물리적으로 분리

```
┌─────────────────────────────────────────────────────────────┐
│                        apps:api                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │  domain/*   │──│  global/*   │──│   Facade    │          │
│  │ Controller  │  │Config/OAuth │  │ Home/Disco  │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
└───────────────────────────┬─────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│  modules:user │   │modules:content│   │modules:collect│ ...
├───────────────┤   ├───────────────┤   ├───────────────┤
│    domain     │   │    domain     │   │    domain     │
│   repository  │   │   repository  │   │   repository  │
│    service    │   │    service    │   │    service    │
│      dto      │   │      dto      │   │      dto      │
│   exception   │   │   exception   │   │   exception   │
└───────┬───────┘   └───────┬───────┘   └───────┬───────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            ▼
                ┌───────────────────────┐
                │    modules:shared     │
                ├───────────────────────┤
                │  Base, BaseTime       │
                │  GeneralException     │
                │  QueryDslUtil         │
                │  PaginationResponse   │
                └───────────────────────┘
```

### 핵심 원칙

1. **Modular Monolith**: 도메인별 모듈 분리, ArchUnit으로 의존성 강제
2. **DDD**: 도메인 주도 설계, Aggregate 경계 준수
3. **SOLID**: 단일 책임, 개방-폐쇄, 의존성 역전 원칙
4. **Clean Code**: 가독성, 테스트 가능성, 유지보수성

## 모듈 의존성 규칙

```
apps:api
   ├── modules:* (모든 도메인 모듈)
   │      │
   │      └── modules:shared (모든 도메인 모듈이 의존)
   └── infra:* (redis, s3)
```

| 규칙 | 설명 |
|------|------|
| `apps:*` → `modules:*`, `infra:*` | API 모듈은 모든 도메인/인프라 모듈 의존 가능 |
| `modules:*` → `modules:shared`만 | 도메인 모듈 간 직접 의존 금지 |
| `infra:*` → 외부 라이브러리만 | 인프라 모듈은 도메인 의존 금지 |
| 엔티티는 단일 모듈 소유 | 다른 모듈은 ID로만 참조 |
| 모듈 간 순환 의존 금지 | ArchUnit으로 자동 검증 |

---

## 모듈 상세

### apps:api

REST API 애플리케이션 모듈. 모든 도메인 모듈을 조합하여 API를 제공합니다.

| 패키지 | 역할 |
|--------|------|
| `domain/*/controller` | REST 엔드포인트 |
| `domain/*/service/*Facade` | 여러 모듈을 조합하는 파사드 |
| `domain/*/dto` | API 요청/응답 DTO (request/, response/) |
| `global/config` | Security, Swagger, WebMvc 설정 |
| `global/oauth` | OAuth 클라이언트 (Kakao) |
| `global/security` | JWT 인증 필터 |
| `global/storage` | S3 Presigned URL 서비스 |

---

### modules:shared

모든 모듈에서 공유하는 공통 컴포넌트입니다.

| 패키지 | 역할 |
|--------|------|
| `domain` | Base, BaseTime 등 JPA 공통 엔티티 |
| `dto` | PaginationResponse, PaginationMeta, SliceCursor |
| `exception` | GeneralException, AppError, ProblemDetail (RFC 9457) |
| `util` | QueryDslUtil, SoftDeleteUtil, ExtractUtil |
| `storage` | StoragePath, StorageKeyGenerator, FileExtension |
| `p6spy` | SQL 로깅 포맷터 |

---

### modules:user

사용자 프로필 SoT (Source of Truth)

| Aggregate | 테이블 | 설명 |
|-----------|--------|------|
| `User` | `users` | 사용자 정보 (닉네임, 프로필 이미지, 역할) |

**주요 필드:**
- `user_id` (TSID, PK)
- `nickname` (UNIQUE)
- `profile_image`
- `user_role`
- `user_status` (ACTIVE, WITHDRAWN)

---

### modules:auth

인증/계정 연결 SoT

| Aggregate | 테이블 | 설명 |
|-----------|--------|------|
| `UserIdentity` | `user_identities` | 소셜 로그인 자격 증명 |

**주요 필드:**
- `user_id`
- `provider` (KAKAO, APPLE, GOOGLE)
- `provider_user_id`
- **UNIQUE**: `(provider, provider_user_id)`

**인증 전략:**
- Access Token: JWT (짧은 만료 시간)
- Refresh Token: Redis 저장 (RTR 방식)
- OAuth: apps/api/global/oauth에서 처리

---

### modules:content

작품 메타 SoT

| Aggregate | 테이블 | 설명 |
|-----------|--------|------|
| `Content` | `contents` | 작품 정보 (TMDB 연동) |
| `Genre` | `genres` | 장르 마스터 데이터 |
| `ContentGenre` | `content_genres` | 작품-장르 매핑 |

**주요 필드 (Content):**
- `content_id` (TSID, PK)
- `tmdb_id` (UNIQUE)
- `content_type` (MOVIE, TV, VARIETY)
- `title`, `description`, `poster`
- `release_year`

---

### modules:collection

컬렉션 SoT

| Aggregate | 테이블 | 설명 |
|-----------|--------|------|
| `Collection` | `collections` | 콘텐츠를 묶는 컬렉션 |
| `CollectionContent` | `collection_contents` | 컬렉션-콘텐츠 연결 (1~10개) |
| `RecentViewedCollection` | `recent_viewed_collections` | 최근 조회 기록 |

**주요 필드 (Collection):**
- `collection_id` (TSID, PK)
- `author_user_id`
- `title`, `description`, `collection_image` (nullable, 요청 미지정 시 포스터 fallback 없음)
- `is_public`

**주요 필드 (CollectionContent):**
- `content_id`
- `sort_order` (요청 배열 순서, 0부터 시작)
- `is_spoiler`
- `reason`
- **UNIQUE**: `(collection_id, content_id)`, `(collection_id, sort_order)`

---

### modules:bookmark

유저 저장 SoT

| Aggregate | 테이블 | 설명 |
|-----------|--------|------|
| `ContentBookmark` | `content_bookmarks` | 콘텐츠 북마크 (좋아하는 작품) |
| `CollectionBookmark` | `collection_bookmarks` | 컬렉션 북마크 |

**주요 필드:**
- `user_id`, `content_id` 또는 `collection_id`
- **UNIQUE**: `(user_id, content_id)`, `(user_id, collection_id)`

---

### modules:taste

취향 키워드/프로파일 SoT

> **Note**: Genre(장르)와 Keyword(취향 키워드)는 별도 개념으로 분리됩니다.
> - **Genre**: 작품의 객관적 분류 (로맨스, 액션 등) - `content` 모듈에서 관리
> - **Keyword**: 사용자 취향/감상 키워드 (힐링, 반전, 성장 등) - `taste` 모듈에서 관리

| Aggregate | 테이블 | 설명 |
|-----------|--------|------|
| `Keyword` | `keywords` | 취향 키워드 마스터 |
| `ContentKeyword` | `content_keywords` | 작품-키워드 매핑 |
| `UserKeyword` | `user_keywords` | 사용자별 키워드 비율 |

**주요 필드 (Keyword):**
- `name` (힐링, 반전, 성장, 감동 등)
- **UNIQUE**: `(name)`

**주요 필드 (UserKeyword):**
- `user_id`, `keyword_id`
- `percentage` (0~100, 사용자의 해당 키워드 비율)
- **UNIQUE**: `(user_id, keyword_id)`

---

### modules:ott

OTT 플랫폼/구독 SoT

| Aggregate | 테이블 | 설명 |
|-----------|--------|------|
| `OttProvider` | `ott_providers` | OTT 플랫폼 마스터 (Netflix, Disney+ 등) |
| `ContentOtt` | `content_otts` | 작품별 시청 가능 OTT |
| `UserOtt` | `user_otts` | 유저별 구독 OTT |

**주요 필드 (OttProvider):**
- `name` (UNIQUE)
- `logo_url`

**주요 필드 (ContentOtt, UserOtt):**
- `content_id` 또는 `user_id`
- `ott_provider_id`
- **UNIQUE**: `(content_id, ott_provider_id)`, `(user_id, ott_provider_id)`

---

### modules:search

검색 도메인 (MVP: DB 검색, 후속: OpenSearch)

| Aggregate | 테이블 | 설명 |
|-----------|--------|------|
| - | - | MVP에서는 엔티티 없음 |
| (후속) `SearchIndexCursor` | `search_index_cursors` | 인덱싱 상태 추적 |

---

### infra:redis

Redis 연동 인프라 모듈

- Refresh Token 저장/관리
- Access Token Blacklist
- (후속) 캐싱

---

### infra:s3

AWS S3 연동 인프라 모듈

- Presigned URL 생성
- 파일 업로드/다운로드

---

## Convention

### 레이어드 아키텍처

각 도메인 모듈은 다음 구조를 따릅니다:

```
modules/{domain}/
├── domain/         # 엔티티, VO, Enum
├── repository/     # JPA Repository
├── service/        # 비즈니스 로직
├── dto/            # 내부 DTO (Command, Query 등)
└── exception/      # 도메인 예외
```

### DTO 위치 규칙

| 위치 | 용도 | 예시 |
|------|------|------|
| `apps/api/domain/*/dto/request/` | API 요청 DTO | `CreateCollectionReq` |
| `apps/api/domain/*/dto/response/` | API 응답 DTO | `AuthTokenRes` |
| `modules/*/dto/` | 모듈 내부 DTO | `CollectionCreateCommand` |

### 예외 처리 (RFC 9457)

```java
// 도메인 예외 정의
public enum UserErrorCode implements AppError {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER.NOT_FOUND", "User Not Found", "사용자를 찾을 수 없습니다.");
}

// 예외 발생
throw new GeneralException(UserErrorCode.USER_NOT_FOUND);
```

### Entity 패턴

```java
@Entity
@SQLRestriction("deleted_at IS NULL")  // Soft Delete
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTime {

    // 정적 팩토리 메서드
    public static User create(String nickname) {
        return User.builder()
                .nickname(nickname)
                .status(UserStatus.ACTIVE)
                .build();
    }

    // 도메인 메서드 (상태 변경)
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }
}
```

**Entity 규칙:**
- `Base` 또는 `BaseTime` 상속 (TSID ID, equals/hashCode 자동 적용)
  - `Base`: ID만 필요한 경우 (대부분의 엔티티)
  - `BaseTime`: ID + createdAt/updatedAt이 필요한 경우에만 사용
- 모든 엔티티가 `BaseTime`을 상속받을 필요 없음
- Soft Delete도 필요한 엔티티에만 선택적 적용
- `@Setter` 금지 → 도메인 메서드로 상태 변경
- `Builder` private → 정적 팩토리 메서드로만 생성
- `@SQLRestriction` → Soft Delete 적용 (필요한 엔티티만)

### FK 처리 방식

다른 모듈의 FK는 코드에서 ID만 저장. DDL에서 FK 관리

```java
// ✅ ID로만 참조
@Column(name = "user_id", nullable = false)
private Long userId;

// ❌ 다른 모듈의 @ManyToOne 사용 금지
@ManyToOne
private User user;
```

---

## 정리

| 항목 | 결정 |
|------|------|
| 아키텍처 | Modular Monolith (멀티모듈) |
| 모듈 간 참조 | ID로만 참조, @ManyToOne 금지 |
| FK 제약 | 코드에서는 없음, DDL에서 선택적 추가 |
| 쓰기 | 각 모듈의 Service |
| 읽기 (단순) | apps:api Facade에서 조합 |
| 읽기 (복잡) | apps:api QueryRepository에서 조인 |
| Aggregate 경계 | 함께 변경되면 포함, 아니면 분리 |
| 이벤트 | MVP에서는 Facade, 복잡해지면 도입 |
| 아키텍처 검증 | ArchUnit으로 자동 테스트 |
