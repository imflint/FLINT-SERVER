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
└── .env                        # 환경 변수 (git ignored)
```

## 아키텍처

### Modular Monolith

도메인별 모듈이 독립적으로 분리된 **모듈러 모놀리식** 구조를 채택. Gradle 멀티 모듈로 빌드하여 물리적으로 분리

```
┌─────────────────────────────────────────────────────────────┐
│                        apps:api                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │ Controller  │──│   Facade    │──│   Config    │          │
│  └─────────────┘  └─────────────┘  └─────────────┘          │
└───────────────────────────┬─────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│  modules:user │   │modules:content│   │modules:collect│
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
                │  ExtractUtil          │
                │  PaginationResponse   │
                └───────────────────────┘
```

### 핵심 원칙

1. **Modular Monolith**: 도메인별 모듈 분리, ArchUnit으로 의존성 강제
2. **DDD**: 도메인 주도 설계, Aggregate 경계 준수
3. **SOLID**: 단일 책임, 개방-폐쇄, 의존성 역전 원칙
4. **Clean Code**: 가독성, 테스트 가능성, 유지보수성

### 왜 Modular Monolith인가?

| 항목 | 멀티모듈 | 단일모듈 + 패키지 |
|------|---------|------------------|
| 의존성 강제 | 컴파일 타임 강제 | ArchUnit으로 강제 |
| 조인 쿼리 | apps:api에서 별도 작성 | 자유롭게 가능 |
| 설정 복잡도 | 높음 | 낮음 |
| MSA 전환 | 용이 | 추가 작업 필요 |

**의사결정 근거:**
- 나중에 분리하면 비용이 더 큼
- 처음부터 경계를 강제하면 기술 부채 방지

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
| `UserIdentity` | 소셜 로그인 자격 증명 (provider, providerId) |

### modules:content
콘텐츠 도메인을 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `Content` | 사용자가 저장한 콘텐츠 (URL, 제목, 메모) |
| `Genre` | 장르 마스터 데이터 |
| `ContentGenre` | 콘텐츠-장르 연결 |

### modules:collection
컬렉션 도메인을 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `Collection` | 콘텐츠를 묶는 컬렉션 (제목, 설명, 공개 여부) |
| `CollectionContent` | 컬렉션-콘텐츠 연결 |
| `RecentViewedCollection` | 최근 조회한 컬렉션 |

### modules:bookmark
북마크를 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `CollectionBookmark` | 컬렉션 북마크 |
| `ContentBookmark` | 콘텐츠 북마크 |

### modules:taste
취향 키워드 도메인을 담당합니다.

| Aggregate | 설명 |
|-----------|------|
| `Keyword` | 취향 키워드 마스터 |
| `UserKeyword` | 사용자-키워드 연결 |

### infra:
외부 인프라를 담당

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
   └── infra:
```

| 규칙 | 설명 |
|------|------|
| `apps:*` → `modules:*`, `infra:*` | API 모듈은 모든 도메인/인프라 모듈 의존 가능 |
| `modules:*` → `modules:shared`만 | 도메인 모듈 간 직접 의존 금지 |
| `infra:*` → 외부 라이브러리만 | 인프라 모듈은 도메인 의존 금지 |
| 엔티티는 단일 모듈 소유 | 다른 모듈은 ID로만 참조 |
| 모듈 간 순환 의존 금지 | ArchUnit으로 자동 검증 |

## 모듈 간 통신

### 쓰기 (Command)

각 모듈의 Service에서 독립적으로 처리합니다.

```java
// user 모듈
userService.withdraw(userId);

// bookmark 모듈
bookmarkService.deleteAllByUserId(userId);
```

### 읽기 (Query) - Facade 패턴

**apps:api의 Facade**에서 여러 모듈을 조합합니다.

```java
// apps:api/facade/HomeQueryFacade.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeQueryFacade {
    private final CollectionService collectionService;
    private final BookmarkService bookmarkService;
    private final UserService userService;

    public HomeResponse getHome(Long userId) {
        User user = userService.getById(userId);
        List<Collection> recent = collectionService.findRecentByUserId(userId);
        List<Collection> bookmarked = bookmarkService.findBookmarkedCollections(userId);
        return HomeResponse.of(user, recent, bookmarked);
    }
}
```

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

### Entity 패턴

```java
@SQLRestriction("deleted_at IS NULL")  // Soft Delete
@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTime {

    @Column(nullable = false, length = 10, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    // 정적 팩토리 메서드
    public static User createFling(String realName, String nickname) {
        validateNickname(nickname);
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
- `BaseTime` 상속 (TSID ID + createdAt/updatedAt)
- `@Setter` 금지 → 도메인 메서드로 상태 변경
- `Builder` private → 정적 팩토리 메서드로만 생성
- `@SQLRestriction` → Soft Delete 적용

### 페이지네이션

```java
// Offset 기반
PaginationResponse.ofOffset(page);

// Cursor 기반
PaginationResponse.ofCursor(sliceCursor);
```

## Shared 모듈 유틸리티

### QueryDslUtil

동적 쿼리 조건을 깔끔하게 처리

```java
import static kr.flint.shared.util.QueryDslUtil.*;

return queryFactory
    .selectFrom(collection)
    .where(
        onCondition(keyword, k -> collection.title.contains(k)),  // null이면 조건 무시
        onCondition(isPublic, p -> collection.isPublic.eq(p)),
        onNotEmpty(userIds, ids -> collection.userId.in(ids))     // 빈 컬렉션이면 무시
    )
    .fetch();
```

### ExtractUtil

컬렉션에서 특정 값을 추출

```java
List<User> users = userRepository.findAll();

// ID 추출
List<Long> userIds = ExtractUtil.extractIdList(users);

// 커스텀 필드 추출
List<String> nicknames = ExtractUtil.extractList(users, User::getNickname);
```

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
