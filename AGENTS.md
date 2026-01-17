# Repository Guidelines

## **CRITICAL**
- When unsure about implementation details, ALWAYS ask the developer.
- @PRD.md의 제품을 만들고 있습니다.
- 최대한 **확장성**있고 유지보수하기 간편한 아키텍쳐와 코드를 설계해주세요.
- 작업을 할 때, OOP(객체지향 프로그래밍)를 준수하며 코드를 작성해주세요.
- 수정사항은 전부 PRD.md에 업데이트해주세요.
- 개발할 때 context7으로 공식 문서를 확인하세요.
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

## Project Structure & Module Organization
This is a Gradle multi-module Spring Boot repo with a modular monolith layout.

- `apps/api/`: REST API application module (controllers, facades, config).
- `modules/*`: domain modules (user, auth, content, collection, bookmark, taste, search).
- `modules/shared/`: shared base entities, exceptions, pagination utilities.
- `infra/redis/`, `infra/s3/`: infrastructure integrations.
- `docs/` and `PRD.md`: product and design context.
- Source code lives under `src/main/java`; tests under `src/test/java` in each module.

Module dependency rules: `apps:*` depends on `modules:*` and `infra:*`; domain modules depend only on `modules:shared`; infra modules depend only on external libraries.

## Build, Test, and Development Commands
- `./gradlew build`: build all modules.
- `./gradlew :apps:api:bootRun`: run the API locally.
- `./gradlew test`: run all tests.
- `./gradlew :modules:user:test`: test a single module.
- `./gradlew test --tests "kr.flint.user.service.UserServiceTest.testCreate"`: run one test.

## Coding Style & Naming Conventions
- Java 21, Spring Boot 3.5, Gradle; follow existing formatting (4-space indent, no tabs).
- Prefer Lombok builders and static factory methods for entities.
- Minimize `@Setter`; favor explicit domain methods.
- DTO naming: `*Request` and `*Response` (e.g., `UserCreateRequest`).
- Domain module packages follow `domain/`, `repository/`, `service/`, `dto/`, `exception/`.
- When adding controller endpoints, update the corresponding `ControllerDocs` interface for OpenAPI.

## Testing Guidelines
- JUnit 5 (`useJUnitPlatform`) with Spring Boot test support.
- Place tests in `src/test/java`, name classes `*Test`.
- Focus on repository and service layer tests per project convention.

## Commit & Pull Request Guidelines
- Commit messages follow a type prefix (e.g., `feat:`, `refactor:`, `chore:`, `test:`, `init:`); include issue/PR references when available (e.g., `feat: add redis cache (#7)`).
- Use the PR template in `.github/pull_request_template.md`:
  - link related issue (`close #...`),
  - summarize changes,
  - note questions/PR points,
  - attach Postman screenshots for API changes.

## Configuration & Secrets
- `.env` is git-ignored; set DB/Redis/S3 env vars locally as needed.
