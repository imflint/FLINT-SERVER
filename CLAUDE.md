# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.


## **CRITICAL**
- When unsure about implementation details, ALWAYS ask the developer.
- @PRD.md의 제품을 만들고 있습니다.
- 최대한 **확장성**있고 유지보수하기 간편한 아키텍쳐와 코드를 설계해주세요.
- 작업을 할 때, OOP(객체지향 프로그래밍)를 준수하며 코드를 작성해주세요.
- 수정사항은 전부 PRD.md에 업데이트해주세요.
- 개발할 때 context7으로 공식 문서를 확인하세요.
- ExtractUtil과 QueryDslUtil을 확인하고 적절하게 사용하세요.
- flyway 마이그레이션 파일은 작성하지 않아도 됩니다.
- Testable한 코드를 작성해주세요.
- 작업을 할 때, 테스트 코드는 Repository와 Service 레이어만 만듭니다.
- 작업을 할 때, 관련 코드들을 확인하고 구현합니다.
- 재사용할 수 있는 메서드나 코드가 있다면 새로 만들지 말고 재사용해주세요.
- 클래스 단위의 주석은 달지 마세요. 메서드에만 간략한 주석을 //로 작성합니다. 메서드가 복잡한지 판단하고 복잡하다면 상세한 주석을 달아주세요.
- 빌더와 정적 팩토리 메서드를 사용하여 개발합니다.
- 도메인 주도 개발(Domain Driven Development) 원칙을 준수하여 작업을 진행합니다.
- SOLID 원칙을 준수하며 책임 분리와 확장성 좋은 코드를 작성해주세요.
- @Setter 어노테이션의 사용은 최소화하고 꼭 필요한 필드에만 메서드로 정의해서 사용해주세요.
- 값을 단순히 가져오기만 하는 Getter 메서드의 사용은 피하고 필요한 행동을 정의해서 사용해주세요.
- Controller에 새로운 메서드를 생성할 땐 ControllerDocs 인터페이스에 해당 메서드의 OpenAPI 명세를 작성해주세요.
- 단순 조회용 API를 구현하는 경우에는 interface projection을 사용해서 필요한 필드만 조회해주세요.
- 관련 로직이 삭제되는 것이 아닌 이상, 절대 기존의 테스트 코드를 삭제하지 마세요.


## Build & Test Commands

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :modules:user:build
./gradlew :apps:api:build

# 테스트 실행
./gradlew test
./gradlew :modules:user:test

# 단일 테스트 실행
./gradlew test --tests "kr.flint.user.service.UserServiceTest.testCreate"

# API 애플리케이션 실행
./gradlew :apps:api:bootRun
```

## Architecture

**Modular Monolith** 구조로, 도메인별 모듈이 독립적으로 분리되어 있습니다.

### Module Dependency Rules

```
apps:api
   ├── modules:* (모든 도메인 모듈)
   └── infra:redis

modules:* → modules:shared만 의존 (다른 도메인 모듈 의존 금지)
infra:*   → 외부 라이브러리만 의존
```

- 엔티티는 단일 모듈이 소유, 다른 모듈은 ID로만 참조
- 모듈 간 순환 의존 금지 (ArchUnit으로 강제)

### Domain Module Structure

각 도메인 모듈(`modules/{domain}`)은 동일한 레이어 구조를 따릅니다:

```
domain/       # 엔티티, VO, Enum
repository/   # JPA Repository + QueryDSL Custom
service/      # 비즈니스 로직
dto/
  request/    # 요청 DTO
  response/   # 응답 DTO
exception/    # 도메인별 ErrorCode enum
```

### Shared Module (`modules:shared`)

모든 모듈에서 공유하는 공통 컴포넌트:

- `Base`, `BaseTime`: JPA 엔티티 기본 클래스 (TSID 기반 ID, 생성/수정 시간)
- `AppError`: 도메인 예외 코드 인터페이스
- `GeneralException`: 공통 예외 클래스
- `PaginationResponse`, `SliceCursor`: 페이지네이션 DTO

### API Module (`apps:api`)

- `controller/`: REST 엔드포인트
- `facade/`: 여러 모듈 조합 (HomeQueryFacade, DiscoveryQueryFacade)
- `config/`: Spring Security, Swagger, JPA Auditing 설정

## Conventions

### Entity Pattern

```java
@Entity
@SQLRestriction("deleted_at IS NULL")  // Soft Delete
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTime {

    public static User create(String nickname) {
        return User.builder()
                .nickname(nickname)
                .status(UserStatus.ACTIVE)
                .build();
    }

    // 도메인 메서드 (상태 변경 로직)
    public void withdraw() { ... }
}
```

### Exception Pattern (RFC 9457)

```java
// 도메인 예외 정의
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements AppError {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER.NOT_FOUND", "User Not Found", "사용자를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;
    private final String detail;
}

// 예외 발생
throw new GeneralException(UserErrorCode.USER_NOT_FOUND);
```

### DTO Naming

- Request: `UserCreateRequest`, `UserUpdateRequest`
- Response: `UserResponse`

## Tech Stack

- Java 21, Spring Boot 3.5, Gradle
- MySQL 8.0, JPA/Hibernate, QueryDSL
- Redis (캐싱)
- P6Spy (SQL 로깅)
- Testcontainers (통합 테스트)
- ArchUnit (아키텍처 테스트)

## Where To Look

| 작업 | 위치 | 비고 |
|------|------|------|
| 새 도메인 모듈 추가 | `modules/{domain}/` | domain, repository, service, dto, exception 패키지 생성 |
| 새 API 엔드포인트 | `apps/api/controller/` | ControllerDocs 인터페이스에 OpenAPI 명세 필수 |
| 여러 모듈 조합 로직 | `apps/api/facade/` | XXXQueryFacade, XXXCommandFacade 패턴 |
| 공통 예외 처리 | `modules/shared/exception/` | AppError 구현, GeneralException 사용 |
| 공통 엔티티 상속 | `modules/shared/domain/` | Base, BaseTime, TimeOnly 상속 |
| 동적 쿼리 작성 | `modules/{domain}/repository/` | QueryDslUtil 활용, XXXRepositoryCustom 패턴 |
| Presigned URL 생성 | `modules/shared/storage/` | StorageUrlProvider 인터페이스 사용 |
| Redis 설정 | `infra/redis/` | Local/Prod Config 분리 |
| S3 설정 | `infra/s3/` | Local/Prod Config 분리 |
| 아키텍처 규칙 확인 | `apps/api/src/test/.../ArchitectureTest.java` | 모듈 의존성, 레이어 규칙 |

## Shared Module Utilities

### 엔티티 기본 클래스

| 클래스 | 용도 |
|--------|------|
| `Base` | TSID 기반 ID만 필요한 엔티티 |
| `BaseTime` | ID + 생성/수정 시간 (대부분의 엔티티) |
| `TimeOnly` | 생성/수정 시간만 필요한 경우 |
| `HasId` | ID 추출용 인터페이스 (ExtractUtil과 함께 사용) |

### 유틸리티

| 클래스 | 용도 | 예시 |
|--------|------|------|
| `ExtractUtil` | 컬렉션에서 값 추출 | `extractIdList(users)` → ID 리스트 |
| `QueryDslUtil` | 동적 쿼리 조건 처리 | `onCondition(keyword, k -> user.name.contains(k))` |

### QueryDslUtil 주요 메서드

```java
// 값이 null이 아닐 때만 조건 적용
onCondition(keyword, k -> user.name.contains(k))

// boolean 조건에 따라 적용
onCondition(isActive, () -> user.status.eq(ACTIVE))

// 컬렉션이 비어있지 않을 때만 적용
onNotEmpty(ids, idList -> user.id.in(idList))
```

## Domain Modules

현재 프로젝트의 도메인 모듈 목록:

| 모듈 | 패키지 | 설명 |
|------|--------|------|
| user | `kr.flint.user` | 사용자 |
| auth | `kr.flint.auth` | 인증 (UserIdentity) |
| content | `kr.flint.content` | 작품 |
| collection | `kr.flint.collection` | 컬렉션 |
| bookmark | `kr.flint.bookmark` | 북마크 |
| taste | `kr.flint.taste` | 취향 |
| search | `kr.flint.search` | 검색 |

## Anti-Patterns (하지 말 것)

| Anti-Pattern | 이유 | 대신 사용할 것 |
|--------------|------|----------------|
| 모듈 간 직접 의존 | ArchUnit 테스트 실패 | ID로만 참조, Facade에서 조합 |
| `@Setter` 남용 | 불변성 훼손 | 도메인 메서드로 상태 변경 |
| 단순 Getter 사용 | 빈약한 도메인 모델 | 행위를 정의하는 메서드 사용 |
| Repository 직접 호출 (다른 모듈) | 캡슐화 위반 | Service 통해 접근 |
| 테스트 없이 Service 구현 | 품질 저하 | Repository, Service 테스트 필수 |
| 클래스 레벨 주석 | 노이즈 | 복잡한 메서드에만 간략히 `//` |
| flyway 마이그레이션 직접 작성 | 별도 관리 | 스킵 |
| `new` 키워드로 엔티티 생성 | 일관성 부족 | 정적 팩토리 메서드 `create()` |

## Architecture Rules (ArchUnit으로 강제)

```
✓ 도메인 모듈 → apps 패키지 의존 금지
✓ shared 모듈 → 도메인 모듈 의존 금지
✓ infra 모듈 → 도메인/apps 의존 금지
✓ 모듈 간 순환 의존 금지
✓ 외부에서 다른 모듈의 domain/repository 직접 접근 금지
✓ domain 레이어 → service/repository/dto 의존 금지
✓ repository 레이어 → service 의존 금지
```

## Complexity Hotspots

| 파일 | 라인 | 설명 |
|------|------|------|
| `ArchitectureTest.java` | 259 | 모듈 의존성/레이어 규칙 테스트 |
| `RedisProdConfig.java` | 99 | Redis 운영 환경 설정 |
| `RedisLocalConfig.java` | 95 | Redis 로컬 환경 설정 |
| `User.java` | 88 | 사용자 엔티티 |
| `CustomP6spySqlFormat.java` | 82 | SQL 로깅 포맷터 |
| `QueryDslUtil.java` | 71 | 동적 쿼리 유틸리티 |
