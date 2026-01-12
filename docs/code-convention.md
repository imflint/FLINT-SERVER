# Backend 코드 컨벤션

본 문서는 Flint 백엔드 개발 시 일관된 코드 품질과 가독성을 위한 코딩 스타일 가이드입니다.

## 아키텍처

### Gradle 멀티 모듈 기반 Modular Monolith

**Gradle 멀티 모듈**을 사용하여 도메인별 모듈이 물리적으로 분리된 **모듈러 모놀리식** 구조를 채택합니다.

```
flint-api/
├── apps/api/                    # REST API 애플리케이션
│   ├── domain/                  # 도메인별 컨트롤러, DTO, 파사드
│   │   └── {domain}/
│   │       ├── controller/
│   │       ├── dto/request/     # API 요청 DTO (*Req)
│   │       ├── dto/response/    # API 응답 DTO (*Res)
│   │       └── service/         # Facade (여러 모듈 조합)
│   └── global/                  # 공통 설정 및 인프라
│       ├── config/
│       ├── exception/
│       ├── oauth/
│       ├── security/
│       └── storage/
│
├── modules/{domain}/            # 도메인 모듈
│   ├── domain/                  # 엔티티, VO, Enum
│   ├── repository/              # JPA Repository
│   ├── service/                 # 비즈니스 로직
│   ├── dto/                     # 내부 DTO (*Command, *Info 등)
│   └── exception/               # 도메인 예외
│
└── infra/{name}/                # 인프라 모듈 (redis, s3)
```

**Modular Monolith 채택 이유:**

| 항목 | 멀티모듈 | 단일모듈 + 패키지 |
|------|---------|------------------|
| 의존성 강제 | 컴파일 타임 강제 | ArchUnit으로 강제 |
| 조인 쿼리 | apps:api에서 별도 작성 | 자유롭게 가능 |
| 설정 복잡도 | 높음 | 낮음 |
| MSA 전환 | 용이 | 추가 작업 필요 |

- 나중에 분리하면 비용이 더 큼
- 처음부터 경계를 강제하면 기술 부채 방지

### 모듈 의존성 규칙

| 규칙 | 설명 |
|------|------|
| `apps:*` → `modules:*`, `infra:*` | API 모듈은 모든 도메인/인프라 모듈 의존 가능 |
| `modules:*` → `modules:shared`만 | 도메인 모듈 간 직접 의존 금지 |
| `infra:*` → 외부 라이브러리만 | 인프라 모듈은 도메인 의존 금지 |
| 엔티티는 단일 모듈 소유 | 다른 모듈은 ID로만 참조 |

### DTO 위치 및 네이밍 규칙

| 위치 | 용도 | 네이밍 | 예시 |
|------|------|--------|------|
| `apps/api/domain/*/dto/request/` | API 요청 DTO | `*Req` | `CreateCollectionReq`, `SignupReq` |
| `apps/api/domain/*/dto/response/` | API 응답 DTO | `*Res` | `AuthTokenRes`, `GetCollectionDetailRes` |
| `modules/*/dto/` | 모듈 내부 DTO | `*Command`, `*Info`, `*Result` | `CollectionCreateCommand`, `UserAuthInfo` |

**DTO 분리 원칙:**
- **외부 DTO (Req/Res)**: API 계층에서만 사용, `apps/api`에 위치
- **내부 DTO**: 모듈 간 데이터 전달용, 각 모듈의 `dto/` 패키지에 위치
- 내부 DTO는 `request/`, `response/` 하위 디렉토리 없이 `dto/` 바로 아래에 위치

---

## 구현 규칙

### Validation 처리 위치

| 검증 유형 | 처리 위치 | 방법 | 예시 |
|-----------|----------|------|------|
| 형식 검증 | Request DTO | 어노테이션 | `@NotNull`, `@NotBlank`, `@Size` |
| 비즈니스 로직 검증 | Entity | 정적 팩토리/도메인 메서드 | 닉네임 규칙, 상태 전이 |
| DB 제약 조건 | Entity | `@Column` 어노테이션 | `nullable = false`, `unique = true` |

### 트랜잭션 처리

- **서비스 클래스**: `@Transactional(readOnly = true)` 기본 적용
- **CUD 메서드**: 별도로 `@Transactional` 적용

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional  // CUD 메서드만 별도 적용
    public UserAuthInfo create(String nickname) {
        // 생성 로직
    }

    public User getById(Long userId) {
        // 조회 로직 (readOnly = true 적용됨)
    }
}
```

**적용 이유:**
- 읽기 전용 서비스임을 명확히 표현
- 영속성 컨텍스트 활용 및 지연 로딩 지원
- 변경 감지 등 JPA 이점 활용

### 예외 처리 (RFC 9457)

도메인별 `ErrorCode` enum으로 관리하며, `AppError` 인터페이스를 구현합니다.

```java
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements AppError {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER.NOT_FOUND", "User Not Found", "사용자를 찾을 수 없습니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "USER.DUPLICATE_NICKNAME", "Duplicate Nickname", "이미 사용 중인 닉네임입니다.");

    private final HttpStatus httpStatus;
    private final String code;      // 도메인.에러명
    private final String title;     // 영문 제목
    private final String detail;    // 한글 상세 메시지
}

// 예외 발생
throw new UserException(UserErrorCode.USER_NOT_FOUND);
```

### FK 처리 방식

다른 모듈의 FK는 코드에서 ID만 저장합니다. `@ManyToOne` 사용 금지.

```java
// ✅ ID로만 참조
@Column(name = "user_id", nullable = false)
private Long userId;

// ❌ 다른 모듈의 @ManyToOne 사용 금지
@ManyToOne
private User user;
```

**FK 제약 조건:**
- **코드**: FK 관계 없이 ID만 저장
- **DDL**: 필요에 따라 FK 제약 조건 추가 (Flyway 마이그레이션 등에서 관리)

### 도메인 객체 메서드 재정의

- `List`를 사용하는 필드는 방어적 복사 사용
- `@EqualsAndHashCode`, `@ToString` 사용 금지
  - `Base` 클래스에서 `id` 기반 `equals()`/`hashCode()` 이미 구현됨
  - TSID는 객체 생성 시점에 ID가 할당되므로 안전

```java
// modules/shared의 Base 클래스
@Getter
@MappedSuperclass
public abstract class Base implements HasId {

    @Id
    @Tsid
    @Column(updatable = false)
    private Long id;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Base base = (Base) o;
        return id != null && Objects.equals(id, base.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
```

```java
// List 필드는 방어적 복사로 반환
public List<CollectionContent> getContents() {
    return Collections.unmodifiableList(contents);
}
```

---

## 테스트

### 계층별 테스트 전략

| 테스트 유형 | 도구 | 작성 기준 | 검증 내용 |
|-------------|------|-----------|-----------|
| Repository 테스트 | Testcontainers (MySQL) | 복잡한 쿼리 있을 때 | 쿼리 정확성 검증 |
| Service 테스트 | Mockito | 복잡한 비즈니스 로직 있을 때 | 비즈니스 로직 검증 |

- **Controller 테스트**: 작성하지 않음
- **테스트 DB**: Testcontainers 사용 (H2 사용 금지)
- **단순 로직**: 테스트 생략 가능

---

## 코드 스타일 및 컨벤션

### 클래스 구조

- 클래스 선언부 첫 줄은 띄우고, 마지막 중괄호 전에는 띄우지 않음
- **메서드 순서**: 정적 팩토리 → public → private
- **인터페이스 구현 클래스**: 인터페이스의 메서드 순서를 그대로 유지

---

## 계층별 코드 작성 가이드

### Controller

```java
@RestController
@RequestMapping("/collections")  // 실제 경로: /api/v1/collections
@RequiredArgsConstructor
public class CollectionController implements CollectionControllerDocs {

    private final CollectionCommandFacade collectionCommandFacade;

    @PostMapping
    public ResponseEntity<Void> createCollection(
            Long userId,  // 인증 방식은 추후 결정
            @Valid @RequestBody CreateCollectionReq request
    ) {
        collectionCommandFacade.createCollection(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
```

**Controller 규칙:**
- `ResponseEntity` 사용
- Swagger 문서화는 `*ControllerDocs` 인터페이스로 분리
- 컨트롤러 메서드 이름은 해당 API 이름으로 간주하여 **모든 맥락을 표현할 수 있게 작성**

**API Prefix 자동 적용:**
- `/api/v1` prefix는 `WebMvcConfig`에서 전역 설정됨
- `@RequestMapping("/collections")` → 실제 경로는 `/api/v1/collections`
- `kr.flint.api.*` 패키지의 모든 `@RestController`에 자동 적용

```java
// WebMvcConfig.java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String API_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
                c -> c.isAnnotationPresent(RestController.class)
                        && c.getPackageName().startsWith("kr.flint.api."));
    }
}
```

### Domain (Entity)

#### 기본 엔티티 (Soft Delete 없음)

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Collection extends BaseTime {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "collection_image", nullable = false)
    private String image;

    @Column(nullable = false)
    private boolean isPublic;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int bookmarkCount;

    // 정적 팩토리 메서드
    public static Collection create(String title, String description, String image, boolean isPublic, Long userId) {
        return Collection.builder()
                .title(title)
                .description(description)
                .image(image)
                .isPublic(isPublic)
                .userId(userId)
                .bookmarkCount(0)
                .build();
    }

    // 도메인 메서드 (상태 변경)
    public void increaseBookmarkCount() {
        this.bookmarkCount++;
    }

    public void decreaseBookmarkCount() {
        if (this.bookmarkCount > 0) {
            this.bookmarkCount--;
        }
    }
}
```

#### Soft Delete 엔티티

```java
@SQLRestriction("deleted_at IS NULL")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class User extends BaseTime {

    @Column(nullable = false, length = 10, unique = true)
    private String nickname;

    private String profileImage;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private LocalDateTime deletedAt;

    public static User createFling(String nickname) {
        validateNickname(nickname);
        return User.builder()
                .nickname(nickname)
                .userRole(UserRole.FLING)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }

    private static void validateNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            throw new UserException(UserErrorCode.INVALID_NICKNAME);
        }
    }
}
```

#### 복합 유니크 제약 조건

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_bookmark_collection_user",
            columnNames = {"user_id", "collection_id"}
        )
    }
)
public class CollectionBookmark extends Base {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long collectionId;

    public static CollectionBookmark create(Long userId, Long collectionId) {
        return new CollectionBookmark(userId, collectionId);
    }
}
```

**Entity 규칙:**
- `Base` 또는 `BaseTime` 상속 (TSID ID, equals/hashCode 자동 적용)
  - `Base`: ID만 필요한 경우 (대부분의 엔티티)
  - `BaseTime`: ID + createdAt/updatedAt이 **필요한 경우에만** 사용
- **모든 엔티티가 `BaseTime`을 상속받을 필요 없음** - 실제로 생성/수정 시간 추적이 필요한 엔티티에만 적용
- **Soft Delete도 필요한 엔티티에만 적용** - 모든 엔티티에 일괄 적용하지 않음
  - 예: `User`는 Soft Delete 필요, `CollectionBookmark`는 Hard Delete
- `@Setter` 금지 → 도메인 메서드로 상태 변경
- `Builder` private → 정적 팩토리 메서드로만 생성
- `@SQLRestriction("deleted_at IS NULL")` → Soft Delete 적용 (필요한 엔티티만)
- `@Column`의 `nullable`, `unique` 옵션 **필수 고려**
- `@EqualsAndHashCode`, `@ToString` 사용 금지 (Base에서 처리)
- 복합 유니크는 `@Table(uniqueConstraints = ...)` 사용

### DTO

#### API Request DTO (apps/api)

```java
public record CreateCollectionReq(
        @NotBlank @Size(max = 50) String title,
        @Size(max = 200) String description,
        String imageUrl,
        boolean isPublic,
        @NotNull @Size(min = 1, max = 10) List<AddContentReq> contentList
) {
    // API DTO → 내부 Command 변환
    public CollectionCreateCommand toCommand() {
        return CollectionCreateCommand.of(
                title, description, imageUrl, isPublic,
                contentList.stream().map(AddContentReq::toInput).toList()
        );
    }
}
```

#### API Response DTO (apps/api)

```java
public record AuthTokenRes(
        String accessToken,
        String refreshToken,
        Long userId
) {
    public static AuthTokenRes of(String accessToken, String refreshToken, Long userId) {
        return new AuthTokenRes(accessToken, refreshToken, userId);
    }
}
```

#### 모듈 내부 DTO (modules/*/dto/)

```java
public record UserAuthInfo(
        Long userId,
        String role
) {
    public static UserAuthInfo of(Long userId, String role) {
        return new UserAuthInfo(userId, role);
    }
}
```

```java
public record CollectionCreateCommand(
        String title,
        String description,
        String imageUrl,
        boolean isPublic,
        List<ContentInput> contents
) {
    public static CollectionCreateCommand of(
            String title, String description, String imageUrl,
            boolean isPublic, List<ContentInput> contents
    ) {
        return new CollectionCreateCommand(title, description, imageUrl, isPublic, contents);
    }

    public record ContentInput(Long contentId, boolean isSpoiler, String reason) {
        public static ContentInput of(Long contentId, boolean isSpoiler, String reason) {
            return new ContentInput(contentId, isSpoiler, reason);
        }
    }
}
```

**DTO 규칙:**
- `record` 사용
- **외부 DTO 네이밍**: `*Req`, `*Res`
- **내부 DTO 네이밍**: `*Command`, `*Info`, `*Result`
- 정적 팩토리 메서드: `of()`, `from()`
- API DTO → 내부 DTO 변환: `toCommand()`, `toInput()`
- 엔티티 리스트 → DTO 리스트 변환: `convert()` 메서드 사용

```java
// 엔티티 리스트를 DTO 리스트로 변환
public static List<UserSimpleRes> convert(List<User> users) {
    return users.stream()
            .map(UserSimpleRes::from)
            .toList();
}
```

### Service

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    public UserAuthInfo getAuthInfo(Long userId) {
        User user = getById(userId);
        return UserAuthInfo.of(user.getId(), user.getUserRole().name());
    }

    @Transactional
    public UserAuthInfo create(String nickname) {
        if (existsByNickname(nickname)) {
            throw new UserException(UserErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.createFling(nickname);
        User saved = userRepository.save(user);
        return UserAuthInfo.of(saved.getId(), saved.getUserRole().name());
    }

    public boolean existsByNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }
}
```

### Facade (apps/api)

여러 모듈을 조합하는 경우 Facade 패턴을 사용합니다.

```java
@Service
@RequiredArgsConstructor
public class CollectionCommandFacade {

    private final UserService userService;
    private final CollectionService collectionService;

    @Transactional
    public void createCollection(Long userId, CreateCollectionReq request) {
        userService.getById(userId);  // 사용자 존재 검증
        collectionService.createCollection(userId, request.toCommand());
    }
}
```

### Repository

```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    @Query("SELECT new kr.flint.user.dto.response.UserSimpleRes(u.id, u.nickname, u.profileImage) " +
           "FROM User u WHERE u.id IN :userIds")
    List<UserSimpleRes> findByIds(@Param("userIds") List<Long> userIds);
}
```

**Repository 규칙:**
- JpaRepository 직접 확장
- 복잡한 쿼리는 QueryDSL Custom Repository 사용
- 단순 조회는 Interface Projection 사용

### Test

#### Repository Test

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private UserRepository userRepository;

    @DisplayName("닉네임으로 사용자를 조회한다")
    @Test
    void findByNickname() {
        // given
        User user = User.createFling("testuser");
        userRepository.save(user);

        // when
        Optional<User> found = userRepository.findByNickname("testuser");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("testuser");
    }
}
```

#### Service Test

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @DisplayName("존재하지 않는 사용자 조회 시 예외가 발생한다")
    @Test
    void getById_notFound() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getById(1L))
                .isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }
}
```

---

## Shared 모듈 유틸리티

### QueryDslUtil

동적 쿼리 조건을 깔끔하게 처리합니다. null 체크나 빈 컬렉션 체크를 간결하게 작성할 수 있습니다.

```java
import static kr.flint.shared.util.QueryDslUtil.*;

@Repository
@RequiredArgsConstructor
public class CollectionQueryRepository {

    private final JPAQueryFactory queryFactory;

    // 기존 방식 (null 체크 직접)
    public List<Collection> searchOld(String keyword, Boolean isPublic, List<Long> userIds) {
        return queryFactory
            .selectFrom(collection)
            .where(
                keyword != null ? collection.title.contains(keyword) : null,
                isPublic != null ? collection.isPublic.eq(isPublic) : null,
                userIds != null && !userIds.isEmpty() ? collection.userId.in(userIds) : null
            )
            .fetch();
    }

    // QueryDslUtil 사용 (간결하고 가독성 좋음)
    public List<Collection> search(String keyword, Boolean isPublic, List<Long> userIds) {
        return queryFactory
            .selectFrom(collection)
            .where(
                onCondition(keyword, k -> collection.title.contains(k)),
                onCondition(isPublic, p -> collection.isPublic.eq(p)),
                onNotEmpty(userIds, ids -> collection.userId.in(ids))
            )
            .fetch();
    }

    // boolean 조건 사용 예시
    public List<Collection> findPublicCollections(Long excludeUserId, boolean excludeMine) {
        return queryFactory
            .selectFrom(collection)
            .where(
                collection.isPublic.isTrue(),
                onCondition(excludeMine, () -> collection.userId.ne(excludeUserId))
            )
            .fetch();
    }
}
```

**주요 메서드:**

| 메서드 | 설명 | 사용 예시 |
|--------|------|----------|
| `onCondition(value, predicateFunction)` | 값이 null이 아닐 때만 조건 적용 | `onCondition(keyword, k -> title.contains(k))` |
| `onCondition(boolean, supplier)` | boolean이 true일 때만 조건 적용 | `onCondition(excludeMine, () -> userId.ne(myId))` |
| `onNotEmpty(collection, predicateFunction)` | 컬렉션이 비어있지 않을 때만 적용 | `onNotEmpty(ids, i -> userId.in(i))` |
| `emptyCondition()` | 빈 조건 반환 (조건 무시) | - |

### ExtractUtil

컬렉션에서 특정 값을 추출합니다. `HasId` 인터페이스를 구현한 엔티티에서 ID를 쉽게 추출할 수 있습니다.

```java
import static kr.flint.shared.util.ExtractUtil.*;

@Service
@RequiredArgsConstructor
public class CollectionQueryService {

    private final CollectionRepository collectionRepository;
    private final UserService userService;

    public List<CollectionWithAuthor> getCollectionsWithAuthors() {
        List<Collection> collections = collectionRepository.findAll();

        // ID 추출 (HasId 인터페이스 구현 엔티티)
        List<Long> collectionIds = extractIdList(collections);
        Set<Long> collectionIdSet = extractIdSet(collections);

        // 커스텀 필드 추출
        List<Long> authorIds = extractList(collections, Collection::getUserId);
        Set<Long> uniqueAuthorIds = extractSet(collections, Collection::getUserId);

        // 추출된 ID로 연관 데이터 조회
        List<UserSimpleRes> authors = userService.getUserInfoList(new ArrayList<>(uniqueAuthorIds));

        // ... 조합 로직
    }
}
```

**주요 메서드:**

| 메서드 | 설명 | 반환 타입 |
|--------|------|----------|
| `extractIdList(items)` | ID 리스트 추출 (HasId 구현 필요) | `List<Long>` |
| `extractIdSet(items)` | ID Set 추출 (중복 제거) | `Set<Long>` |
| `extractList(items, mapper)` | 커스텀 필드 리스트 추출 | `List<R>` |
| `extractSet(items, mapper)` | 커스텀 필드 Set 추출 (중복 제거) | `Set<R>` |

---

## 정리

| 항목 | 결정 |
|------|------|
| 아키텍처 | Gradle 멀티 모듈 기반 Modular Monolith |
| API Prefix | `/api/v1` (WebMvcConfig에서 전역 적용) |
| 모듈 간 참조 | ID로만 참조, @ManyToOne 금지 |
| 외부 DTO 위치 | `apps/api/domain/*/dto/request/`, `response/` |
| 외부 DTO 네이밍 | `*Req`, `*Res` |
| 내부 DTO 위치 | `modules/*/dto/` (하위 디렉토리 없음) |
| 내부 DTO 네이밍 | `*Command`, `*Info`, `*Result` |
| Entity 상속 | `Base` (기본) 또는 `BaseTime` (필요한 경우만) |
| Entity 생성 | 정적 팩토리 메서드 (Builder private) |
| Entity 변경 | 도메인 메서드 (@Setter 금지) |
| equals/hashCode | Base 클래스에서 ID 기반으로 구현됨 |
| BaseTime/Soft Delete | 필요한 엔티티에만 선택적 적용 |
| 복합 유니크 | `@Table(uniqueConstraints = ...)` |
| 예외 처리 | RFC 9457 ProblemDetail + AppError enum |
| 테스트 DB | Testcontainers (MySQL) |
| 테스트 범위 | Repository, Service 레이어만 |
| DTO 형식 | Java record |
| API 문서화 | *ControllerDocs 인터페이스 분리 |
