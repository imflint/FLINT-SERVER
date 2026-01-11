# Flint API 아키텍처 문서

---

## 목차

- [1. 아키텍처 개요](#1-아키텍처-개요)
- [2. 모듈 간 통신](#2-모듈-간-통신)
- [3. FK 처리 방식](#3-fk-처리-방식)
- [4. Aggregate 경계](#4-aggregate-경계)
- [5. 모듈 내 패키지 구조](#5-모듈-내-패키지-구조)
- [6. ArchUnit 아키텍처 테스트](#6-archunit-아키텍처-테스트)
- [7. Shared 모듈 유틸리티](#7-shared-모듈-유틸리티)
- [8. 정리](#8-정리)

---

## 1. 아키텍처 개요

### 1.1 Modular Monolith

도메인별 모듈이 독립적으로 분리된 모듈러 모놀리식 구조를 채택합니다.

```
flint-api/
├── apps/
│   └── api/                 # REST API 애플리케이션
├── modules/
│   ├── shared/              # 공통 컴포넌트
│   ├── user/                # 사용자 도메인
│   ├── auth/                # 인증 도메인
│   ├── content/             # 콘텐츠 도메인
│   ├── collection/          # 컬렉션 도메인
│   ├── bookmark/            # 북마크 도메인
│   ├── taste/               # 취향 키워드 도메인
│   ├── ott/                 # OTT 플랫폼 도메인
│   └── search/              # 검색 도메인
└── infra/
    ├── redis/               # Redis 인프라
    ├── aws/               # AWS 인프라
    └── storage/             # 파일 저장소 인프라
```

### 1.2 모듈 의존성 규칙

```
apps:api
   ├── modules:* (모든 도메인 모듈)
   └── infra:redis, infra:s3

modules:* → modules:shared만 의존 (다른 도메인 모듈 의존 금지)
infra:*   → 외부 라이브러리만 의존
```

- 엔티티는 단일 모듈이 소유, 다른 모듈은 **ID로만 참조**
- 모듈 간 순환 의존 금지 (ArchUnit으로 강제)

### 1.3 멀티모듈 선택 이유

| 항목 | 멀티모듈 | 단일모듈 + 패키지 |
|------|---------|------------------|
| 의존성 강제 | 컴파일 타임 강제 | ArchUnit으로 강제 |
| 조인 쿼리 | apps:api에서 별도 작성 | 자유롭게 가능 |
| 설정 복잡도 | 높음 | 낮음 |
| MSA 전환 | 용이 | 추가 작업 필요 |

**선택 근거:**
- PRD에서 도메인 경계가 이미 명확함
- 나중에 분리하면 비용이 더 큼
- 처음부터 경계를 강제하면 기술 부채 방지

### 1.4 현재 도메인 모듈

| 모듈 | 패키지 | Aggregate | 설명 |
|------|--------|-----------|------|
| user | `kr.flint.user` | User | 사용자 |
| auth | `kr.flint.auth` | UserIdentity | 인증 |
| content | `kr.flint.content` | Content, Genre | 작품 |
| collection | `kr.flint.collection` | Collection | 컬렉션 |
| bookmark | `kr.flint.bookmark` | *Bookmark | 북마크/조회 |
| taste | `kr.flint.taste` | Keyword | 취향 |
| ott | `kr.flint.ott` | OttProvider, *Ott | OTT 플랫폼/구독 |
| search | `kr.flint.search` | - | 검색 |
| shared | `kr.flint.shared` | - | 공통 |

---

## 2. 모듈 간 통신

### 2.1 쓰기 (Command)

각 모듈의 Service에서 독립적으로 처리합니다.

```java
// modules:user
userService.withdraw(userId);

// modules:auth
userIdentityService.deleteAllByUserId(userId);
```

### 2.2 읽기 (Query)

**apps:api의 Facade**에서 여러 모듈을 조합합니다.

```java
// apps:api/facade/AuthFacade.java
@Service
@RequiredArgsConstructor
public class AuthFacade {

    private final UserIdentityService userIdentityService;  // auth 모듈
    private final UserService userService;                   // user 모듈

    public LoginResponse login(AuthProvider provider, String providerUserId) {
        // 1. auth 모듈: 소셜 계정으로 UserIdentity 조회
        UserIdentity identity = userIdentityService
            .findByProviderAndProviderUserId(provider, providerUserId)
            .orElseThrow(...);

        // 2. user 모듈: userId로 User 조회
        User user = userService.getById(identity.getUserId());

        // 3. 조합해서 반환
        return LoginResponse.of(user, generateToken(...));
    }
}
```

### 2.3 조인 쿼리가 필요한 경우

성능 이슈 발생 시 **apps:api에 읽기 전용 QueryRepository** 추가 가능합니다.

```java
// apps:api/repository/AuthQueryRepository.java
@Repository
@RequiredArgsConstructor
public class AuthQueryRepository {

    private final JPAQueryFactory queryFactory;

    // 크로스 모듈 조인 (읽기 전용)
    public Optional<LoginProjection> findLoginInfo(AuthProvider provider, String providerUserId) {
        return Optional.ofNullable(
            queryFactory
                .select(new QLoginProjection(user.id, user.nickname, userIdentity.provider))
                .from(userIdentity)
                .join(user).on(user.id.eq(userIdentity.userId))
                .where(...)
                .fetchOne()
        );
    }
}
```

**원칙:**
- 쓰기는 모듈에서
- 읽기는 apps:api에서 (필요시 조인)

### 2.4 이벤트 기반 통신 (확장 시)

MVP에서는 Facade로 충분하며, 복잡해지면 이벤트 도입을 고려합니다.

```java
// 이벤트 발행 (user 모듈)
@Transactional
public void withdraw(Long userId) {
    user.withdraw();
    eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
}

// 이벤트 구독 (content 모듈) - user 모듈 의존 없음
@EventListener
public void onUserWithdrawn(UserWithdrawnEvent event) {
    contentRepository.deleteByUserId(event.userId());
}
```

---

## 3. FK 처리 방식

### 3.1 코드에서는 ID만 저장

```java
// auth 모듈의 UserIdentity
@Column(name = "user_id", nullable = false)
private Long userId;  // FK 없음, 단순 Long
```

### 3.2 DDL에서 FK 제약 (선택)

코드 의존 없이 DB 레벨 무결성을 보장하려면:

```sql
ALTER TABLE user_identities
ADD CONSTRAINT fk_user_identities_user_id
FOREIGN KEY (user_id) REFERENCES users(id);
```

| 방식 | 코드 의존 | DB 무결성 |
|------|----------|----------|
| ID만 저장 | ❌ | ❌ |
| DDL에서 FK | ❌ | ✅ |
| @ManyToOne | ✅ | ✅ |

---

## 4. Aggregate 경계

### 4.1 Aggregate 설계 원칙

| 원칙 | 설명 |
|------|------|
| 트랜잭션 일관성 | 함께 변경되어야 하는 범위 |
| 비즈니스 불변식 | 함께 검증해야 하는 규칙 |
| 독립적 변경 | 각각 따로 변경 가능하면 분리 |

### 4.2 관계별 처리 방식

| 관계 | 처리 |
|------|------|
| **함께 변경** | 같은 aggregate (Collection ↔ CollectionContents) |
| **독립적 변경** | 별도 aggregate, ID 참조 |
| **마스터 데이터** | 독립 aggregate |
| **행동 기록** | 별도 aggregate (Bookmark류) |

### 4.3 모듈별 Aggregate

#### modules:user
```
┌─────────────────┐
│ User (root)     │
└─────────────────┘
```

#### modules:auth
```
┌─────────────────┐
│ UserIdentity    │ ──→ user_id 참조
└─────────────────┘
```

#### modules:content
```
┌─────────────────┐
│ Content (root)  │
└─────────────────┘

┌─────────────────┐
│ Genre (마스터)  │
└─────────────────┘

┌─────────────────┐
│ GenreContent    │ ──→ content_id, genre_id 참조
└─────────────────┘
```

#### modules:ott
```
┌─────────────────┐
│ OttProvider     │  ← OTT 서비스 마스터 (Netflix, Disney+ 등)
│ (root)          │
└─────────────────┘

┌─────────────────┐
│ ContentOtt      │ ──→ content_id, ott_provider_id 참조
└─────────────────┘

┌─────────────────┐
│ UserOtt         │ ──→ user_id, ott_provider_id 참조
└─────────────────┘
```
- OttProvider: Netflix, Disney+, Wavve 등 OTT 서비스 목록 (마스터 데이터)
- ContentOtt: 작품별 시청 가능한 OTT 플랫폼 (다대다, content_id는 ID 참조)
- UserOtt: 유저별 구독 중인 OTT 플랫폼 (다대다, user_id는 ID 참조)

#### modules:collection
```
┌─────────────────────────┐
│ Collection (root)       │
├─────────────────────────┤
│ CollectionContents      │  ← 함께 변경됨 (같은 aggregate)
└─────────────────────────┘
```
- CollectionContents는 Collection과 함께 추가/삭제/순서변경
- Collection 삭제 시 CollectionContents도 삭제

#### modules:bookmark
```
┌─────────────────────────┐
│ CollectionBookmark      │ ──→ user_id, collection_id 참조
└─────────────────────────┘

┌─────────────────────────┐
│ ContentBookmark         │ ──→ user_id, content_id 참조
└─────────────────────────┘

┌─────────────────────────┐
│ RecentViewedCollection  │ ──→ user_id, collection_id 참조
└─────────────────────────┘
```
- 각각 독립적인 사용자 행동 기록
- User, Collection, Content와 독립적으로 추가/삭제

#### modules:taste
```
┌─────────────────────────┐
│ Keyword (마스터)        │  ← 무드 기반 취향 키워드
└─────────────────────────┘

┌─────────────────────────┐
│ContentPreferenceKeyword │ ──→ content_id, keyword_id 참조
└─────────────────────────┘

┌─────────────────────────┐
│ UserPreferenceKeyword             │ ──→ user_id, keyword_id 참조
└─────────────────────────┘
```

### 4.4 Genre vs Keyword

| 구분 | Genre | Keyword |
|------|-------|---------|
| **출처** | TMDB 등 외부 소스 | 서비스 자체 정의 |
| **성격** | 작품의 공식 장르 | 무드 기반 취향 |
| **예시** | 액션, 로맨스, 스릴러 | 따뜻한, 힐링되는, 몰입감 있는 |
| **용도** | 작품 분류 | 사용자 취향 매칭 |
| **소속** | modules:content | modules:taste |

---

## 5. 모듈 내 패키지 구조

### 5.1 기본 구조

```
modules/{domain}/
├── domain/           # 엔티티, VO, Enum
├── repository/       # JPA Repository
├── service/          # 비즈니스 로직
├── dto/
│   ├── request/      # 요청 DTO
│   └── response/     # 응답 DTO
└── exception/        # 도메인별 ErrorCode, Exception
```

### 5.2 여러 Aggregate가 있는 경우

#### 옵션 1: 플랫 구조 (Aggregate가 단순하고 적을 때)

```
modules/bookmark/
├── domain/
│   ├── CollectionBookmark.java
│   ├── ContentBookmark.java
│   └── RecentViewedCollection.java
├── repository/
│   ├── CollectionBookmarkRepository.java
│   ├── ContentBookmarkRepository.java
│   └── RecentViewedCollectionRepository.java
└── service/
    └── BookmarkService.java
```

#### 옵션 2: Aggregate별 패키지 (복잡할 때)

```
modules/content/
├── content/
│   ├── domain/
│   │   └── Content.java
│   ├── repository/
│   │   └── ContentRepository.java
│   └── service/
│       └── ContentService.java
└── genre/
    ├── domain/
    │   ├── Genre.java
    │   └── GenreContent.java
    └── repository/
        └── GenreRepository.java
```

#### modules:ott 패키지 구조

```
modules/ott/
├── domain/
│   ├── OttProvider.java
│   ├── ContentOtt.java
│   └── UserOtt.java
├── repository/
│   ├── OttProviderRepository.java
│   ├── ContentOttRepository.java
│   └── UserOttRepository.java
└── service/
    └── OttService.java
```

### 5.3 Aggregate 간 참조

```java
// ❌ 다른 aggregate를 직접 참조 (같은 모듈이어도)
public class GenreContent {
    @ManyToOne
    private Content content;
}

// ✅ ID로 참조
public class GenreContent {
    private Long contentId;
    private Long genreId;
}
```

---

## 6. ArchUnit 아키텍처 테스트

ArchUnit을 사용하여 아키텍처 규칙을 자동으로 검증합니다.

### 6.1 테스트 위치

```
apps/api/src/test/java/kr/flint/api/ArchitectureTest.java
```

### 6.2 검증되는 규칙

#### 모듈 의존성 규칙

| 규칙 | 설명 |
|------|------|
| 도메인 모듈 → apps 의존 금지 | `modules:*`가 `apps:api`에 의존하면 안 됨 |
| shared → 도메인 모듈 의존 금지 | `shared`는 다른 도메인 모듈에 의존하면 안 됨 |
| infra → 도메인/apps 의존 금지 | `infra:*`는 도메인 로직에 의존하면 안 됨 |
| 순환 의존 금지 | 모듈 간 순환 참조 불가 |

#### 모듈 캡슐화 규칙

| 규칙 | 설명 |
|------|------|
| domain 패키지 직접 접근 금지 | 외부에서 다른 모듈의 `domain` 패키지 접근 불가 |
| repository 패키지 직접 접근 금지 | 외부에서 다른 모듈의 `repository` 패키지 접근 불가 |

#### 레이어드 아키텍처 규칙

| 규칙 | 설명 |
|------|------|
| domain → 다른 레이어 의존 금지 | `domain`은 `service`, `repository`, `dto` 의존 불가 |
| repository → service 의존 금지 | `repository`가 `service`에 의존하면 안 됨 |

#### 네이밍 컨벤션 규칙

| 규칙 | 설명 |
|------|------|
| *Repository → repository 패키지 | Repository 인터페이스는 repository 패키지에 위치 |
| *Service → service 패키지 | Service 클래스는 service 패키지에 위치 |
| *Exception → exception 패키지 | Exception 클래스는 exception 패키지에 위치 |

### 6.3 테스트 실행

```bash
./gradlew :apps:api:test --tests "kr.flint.api.ArchitectureTest"
```

### 6.4 규칙 위반 시

ArchUnit 테스트가 실패하면 빌드도 실패합니다. 위반 사항을 수정한 후 다시 테스트를 실행하세요.

---

## 7. Shared 모듈 유틸리티

### 7.1 엔티티 기본 클래스

| 클래스 | 용도 | 필드 |
|--------|------|------|
| `Base` | ID만 필요한 엔티티 | TSID 기반 ID |
| `BaseTime` | 대부분의 엔티티 | ID + createdAt + updatedAt |
| `TimeOnly` | 감사 정보만 필요 | createdAt + updatedAt |
| `HasId` | ID 추출용 인터페이스 | `getId()` |

```java
// 대부분의 엔티티
public class User extends BaseTime { ... }

// 감사 시간 불필요한 경우
public class CollectionBookmark extends Base { ... }
```

### 7.2 ExtractUtil

컬렉션에서 특정 값을 추출하는 유틸리티

```java
List<User> users = userRepository.findAll();

// ID만 추출
List<Long> userIds = ExtractUtil.extractIdList(users);
Set<Long> userIdSet = ExtractUtil.extractIdSet(users);

// 커스텀 필드 추출
List<String> nicknames = ExtractUtil.extractList(users, User::getNickname);
```

### 7.3 QueryDslUtil

QueryDSL에서 동적 조건을 처리하는 유틸리티

```java
import static kr.flint.shared.util.QueryDslUtil.*;

// 값이 null이 아닐 때만 조건 적용
return queryFactory
    .selectFrom(collection)
    .where(
        onCondition(keyword, k -> collection.title.contains(k)),
        onCondition(status, s -> collection.status.eq(s)),
        onNotEmpty(userIds, ids -> collection.userId.in(ids))
    )
    .fetch();
```

#### 주요 메서드

| 메서드 | 설명 |
|--------|------|
| `emptyCondition()` | 빈 조건 (항상 true) |
| `onCondition(boolean, Supplier)` | 조건이 true일 때만 Predicate 적용 |
| `onCondition(T, Function)` | 값이 null이 아닐 때만 Predicate 적용 |
| `onNotEmpty(Collection, Function)` | 컬렉션이 비어있지 않을 때만 적용 |

### 7.4 페이지네이션

```java
// Offset 기반
PaginationResponse.ofOffset(page);

// Cursor 기반
PaginationResponse.ofCursor(sliceCursor);
```

---

## 8. 정리

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
