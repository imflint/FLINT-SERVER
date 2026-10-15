# Flint API 요구사항 명세서

> 최종 업데이트: 2026-09-13
> 버전: MVP 1.1

---

## 1. 프로젝트 개요

### 1.1 서비스 소개

**Flint**는 사용자들이 좋아하는 영화/드라마/예능 콘텐츠를 컬렉션으로 큐레이션하고 공유하는 소셜 플랫폼입니다.

### 1.2 핵심 가치

- 취향 기반 콘텐츠 큐레이션
- 사용자 간 컬렉션 공유 및 발견
- 개인화된 추천 시스템 (후속 확장)

### 1.3 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Database | MySQL 8.0 |
| Cache | Redis |
| Storage | AWS S3 + CloudFront |
| Build | Gradle |
| ORM | JPA/Hibernate, QueryDSL |
| 외부 API | TMDB, Kakao OAuth, OpenAI |

---

## 2. 시스템 아키텍처

### 2.1 모듈 구조

```
flint-api/
├── apps/
│   ├── api                    # 사용자 및 관리자 REST API 애플리케이션
│   └── batch                  # API JVM에 조립되는 TMDB Spring Batch 구성
├── modules/
│   ├── shared                 # 공통 컴포넌트 (Base, Exception, DTO)
│   ├── user                   # 사용자 관리
│   ├── auth                   # 인증/인가
│   ├── content                # 콘텐츠(작품) 관리
│   ├── collection             # 컬렉션 관리
│   ├── bookmark               # 북마크 관리
│   ├── taste                  # 취향 키워드 관리
│   ├── ott                    # OTT 플랫폼 관리
│   ├── search                 # 검색
│   ├── admin                  # 관리자 계정
│   └── moderation             # 신고/제재
└── infra/
    ├── redis                  # Redis 캐시
    ├── storage                # S3, CloudFront
    ├── gpt                    # OpenAI 연동
    └── tmdb                   # TMDB API 연동
```

### 2.2 의존성 규칙

- `apps:api` → `apps:batch`, `modules:*`, `infra:*` 의존 가능
- `apps:batch` → TMDB 배치에 필요한 `modules:*`, `infra:tmdb` 의존 가능
- `modules:*` → `modules:shared`만 의존 (다른 도메인 모듈 의존 금지)
- `infra:*` → 외부 라이브러리만 의존
- 엔티티는 단일 모듈 소유, 다른 모듈은 ID 참조

---

## 3. 기능 요구사항

### 3.1 인증 (Auth)

---

#### 3.1.1 소셜 로그인 - 카카오

`POST /auth/social/verify`

**[입력]**

- provider (String): 소셜 로그인 제공자 (MVP: "KAKAO")
- code (String, optional): Web용 - 카카오 OAuth 인가 코드
- accessToken (String, optional): Mobile용 - 카카오 SDK에서 발급받은 액세스 토큰

> code 또는 accessToken 중 하나는 필수

**[처리 로직 - Web 플로우 (code)]**

```
사용자 → 카카오 로그인 페이지 → redirect_uri로 code 전달
    ↓
클라이언트 → 서버에 code 전송
    ↓
서버 → 카카오에 code로 access_token 교환
    ↓
서버 → 카카오에 access_token으로 사용자 정보 조회
    ↓
서버 → 클라이언트에 인증 결과 반환
```

**[처리 로직 - Mobile 플로우 (accessToken)]**

```
사용자 → 카카오 SDK로 로그인 → 앱에 access_token 직접 반환
    ↓
클라이언트 → 서버에 access_token 전송
    ↓
서버 → 카카오에 access_token으로 사용자 정보 조회
    ↓
서버 → 클라이언트에 인증 결과 반환
```

**[분기 처리]**

- **기존 회원**
    - 로그인 처리
    - Access Token / Refresh Token 발급
    - 응답: `{ isRegistered: true, accessToken, refreshToken, userId }`
- **신규 회원**
    - 임시 토큰(tempToken) 발급 (30분 유효)
    - 응답: `{ isRegistered: false, tempToken }`
    - 프로필/닉네임 설정 단계로 이동

**[요청 예시]**

```json
// Web (code 사용)
{ "provider": "KAKAO", "code": "authorization_code_here" }

// Mobile (accessToken 사용)
{ "provider": "KAKAO", "accessToken": "access_token_from_kakao_sdk" }
```

---

#### 3.1.2 이미지 업로드용 Presigned URL 발급

`GET /storage/presigned-url` (Presigned URL 단건 발급)

**[입력]**

- Authorization Header: Bearer {accessToken}
- pathType (StoragePathType): 저장 경로 타입 (예: USER_PROFILE, COLLECTION_THUMBNAIL, COLLECTION_CONTENT)
- extension (FileExtension): 파일 확장자 (예: JPG, JPEG, PNG)

`POST /storage/presigned-urls` (Presigned URL 다건 발급)

**[입력]**

- Authorization Header: Bearer {accessToken}
- items (Array): 발급할 URL 대상 목록 (1~20개)
- items[].pathType (StoragePathType): 저장 경로 타입
- items[].extension (FileExtension): 파일 확장자

**[처리 로직]**

1. 요청 items 순서대로 S3 Presigned URL 발급 (PUT 권한, 10분 유효)
2. 클라이언트에 Presigned URL 및 S3 객체 키 반환
3. 클라이언트는 Presigned URL로 이미지 파일 직접 업로드
4. 스토리지(S3)에 이미지 저장
5. (CloudFront 활성화 시) CDN URL로 접근 가능

**[응답]**

- 단건: `{ uploadUrl, key }`
- 다건: `{ urls: [{ uploadUrl, key }] }`

---

#### 3.1.3 닉네임 유효성 검사

`GET /users/nickname/check`

**[입력]**

- nickname (String): 검증할 닉네임

**[검증 로직]**

1. 길이 검증 (2 ~ 14자)
2. 허용 문자 정규식 검증 (한글, 영문, 숫자만 허용)
3. DB 중복 여부 검사

**[응답]**

- available (Boolean): 사용 가능 여부
- message (String): 검증 결과 메시지

---

#### 3.1.4 회원가입

`POST /auth/signup`

**[입력]**

- tempToken (String): 소셜 로그인 시 발급받은 임시 토큰
- nickname (String): 사용자 닉네임
- profileImage (String, optional): 프로필 이미지 URL
- favoriteContentIds (List<Long>): 좋아하는 콘텐츠 ID 목록
- subscribedOttIds (List<Long>): 구독 중인 OTT ID 목록

**[처리 로직]**

1. tempToken 검증 (유효성, 만료 여부)
2. tempToken에서 provider, providerUserId 추출
3. User 엔티티 생성 (닉네임, 프로필 이미지)
4. UserIdentity 엔티티 생성 (소셜 연동 정보)
5. ContentBookmark 생성 (좋아하는 콘텐츠)
6. UserOtt 생성 (구독 OTT)
7. 취향 분석 이벤트 발행 (비동기, 트랜잭션 커밋 후)
8. Access Token / Refresh Token 발급

**[응답]**

- accessToken (String)
- refreshToken (String)

---

#### 3.1.5 토큰 갱신

`POST /auth/refresh`

**[입력]**

- refreshToken (String): 기존 Refresh Token

**[처리 로직]**

1. Refresh Token 존재 여부 확인 (Redis)
2. 토큰 상태 확인 (VALID / USED / REVOKED)
3. **토큰 재사용 감지 시**: 해당 사용자의 모든 토큰 무효화 (보안)
4. 기존 Refresh Token을 USED 상태로 변경
5. 새로운 Access Token / Refresh Token 발급

**[응답]**

- accessToken (String): 새 Access Token
- refreshToken (String): 새 Refresh Token (Rotation)

---

#### 3.1.6 로그아웃

`POST /auth/logout`

**[입력]**

- refreshToken (String): 무효화할 Refresh Token
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. Access Token을 Blacklist에 추가 (남은 TTL 동안)
2. Refresh Token 상태를 REVOKED로 변경
3. Refresh Token 삭제

---

#### 3.1.7 전체 로그아웃

`POST /auth/logout/all`

**[입력]**

- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. Access Token을 Blacklist에 추가
2. 해당 사용자의 모든 Refresh Token 삭제

---

### 3.2 사용자 (User)

---

#### 3.2.1 사용자 프로필 조회

`GET /users/{userId}`

**[입력]**

- userId (Long, Path): 조회할 사용자 ID

**[처리 로직]**

1. userId로 사용자 정보 조회
2. 프로필 정보 반환

**[응답]**

- userId (Long)
- nickname (String)
- profileImage (String)

---

#### 3.2.2 사용자 취향 키워드 조회

`GET /users/{userId}/keywords`

**[입력]**

- userId (Long, Path): 조회할 사용자 ID

**[처리 로직]**

1. UserKeyword 테이블에서 해당 사용자의 키워드를 순위, 비율, 이름 순으로 조회
2. 재계산 결과는 등록된 서로 다른 키워드가 정확히 6개인지 검증하고, 조회 응답은 최대 6개의 순위를 1~6으로 정규화
3. 1~3위는 코어 키워드, 4~6위는 서브 키워드로 사용
4. 재계산 결과는 기존 키워드 전체를 교체하며 GPT가 동점 순위를 반환해도 고유 순위를 저장
5. 1~3위는 기존 레벨 색상을 우선 사용하되 중복 시 `PINK → GREEN → ORANGE → YELLOW → BLUE` 순으로 미사용 색상을 배정
6. 4~6위는 기존 레벨 색상을 유지
7. 누락·중복·미등록·음수 비율이면 GPT 분석을 한 번 재시도하고, 두 번째도 실패하면 기존 데이터를 유지한 채 오류 반환
8. 비율은 largest remainder 방식으로 정수 합계를 정확히 100으로 정규화하며, 전체 가중치가 0이면 `17, 17, 17, 17, 16, 16` 적용

**[응답]**

- keywords: 키워드 목록
    - name (String): 키워드명
    - percentage (Integer): 비율 (0~100)
    - color (String): 표시 색상
    - rank (Integer): 정규화된 순위 (1~6)
    - group (String): 1~3위 `CORE`, 4~6위 `SUB`

---

#### 3.2.3 사용자 컬렉션 목록 조회

`GET /users/{userId}/collections`

**[입력]**

- userId (Long, Path): 조회할 사용자 ID
- Authorization Header (optional): 로그인 사용자 확인용

**[처리 로직]**

1. 요청자가 본인인지 확인
2. 해당 사용자가 작성한 컬렉션 목록 조회

**[분기 처리]**

- **본인 조회**: 공개 + 비공개 컬렉션 모두 반환
- **타인 조회**: 공개 컬렉션만 반환
- `imageList`는 컬렉션에 포함된 작품의 공식 포스터만 최대 2개 반환하며 사용자 업로드 이미지를 포함하지 않음

---

#### 3.2.4 사용자 북마크 컬렉션 목록 조회

`GET /users/{userId}/bookmarked-collections`

**[입력]**

- userId (Long, Path): 조회할 사용자 ID
- Authorization Header (optional): 로그인 사용자 확인용

**[처리 로직]**

1. 요청자가 본인인지 확인
2. 해당 사용자가 북마크한 컬렉션 목록 조회

**[분기 처리]**

- **본인 조회**: 북마크한 모든 컬렉션 반환 (비공개 포함)
- **타인 조회**: 북마크한 공개 컬렉션만 반환
- `imageList`는 컬렉션에 포함된 작품의 공식 포스터만 최대 2개 반환하며 사용자 업로드 이미지를 포함하지 않음

---

#### 3.2.5 사용자 북마크 콘텐츠 목록 조회

`GET /users/{userId}/bookmarked-contents`

**[입력]**

- userId (Long, Path): 조회할 사용자 ID
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. userId 사용자가 북마크한 콘텐츠 목록 조회
2. 로그인한 사용자가 각 콘텐츠를 북마크했는지 확인
3. 콘텐츠 상세 정보와 로그인 사용자 기준 북마크 여부를 함께 반환

**[응답]**

- totalCount (Integer): 반환된 콘텐츠 수
- contents: 북마크한 콘텐츠 목록
    - id (Long): 콘텐츠 ID
    - title (String): 콘텐츠 제목
    - author (String, nullable): 영화 감독 또는 TV creator. 없으면 `null`
    - imageUrl (String): 콘텐츠 이미지 URL
    - year (Integer): 개봉/방영 연도
    - bookmarkCount (Integer): 북마크 수
    - isBookmarked (Boolean): 로그인한 사용자의 콘텐츠 북마크 여부
    - getOttSimpleList (Array): 조회 대상 사용자가 구독 중이며 해당 콘텐츠를 제공하는 OTT 목록

---

#### 3.2.6 취향 키워드 재계산

`PATCH /users/recalculate/keyword`

**[입력]**

- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 사용자가 북마크한 콘텐츠 목록 조회
2. 콘텐츠 정보를 GPT에 전달
3. GPT 응답으로 취향 키워드 분석
4. UserKeyword 테이블 업데이트

---

### 3.3 컬렉션 (Collection)

---

#### 3.3.1 컬렉션 생성

`POST /collections`

**[입력]**

- title (String): 컬렉션 제목
- description (String, optional): 컬렉션 설명
- isPublic (Boolean): 공개 여부
- contents: 콘텐츠 목록 (1~10개)
    - contentId (Long): 콘텐츠 ID
    - isSpoiler (Boolean): 스포일러 여부
    - reason (String): 선정 이유
    - customImages (String[], optional): 작품별 커스텀 이미지 key 목록. 서버는 개수 제한을 검증하지 않음

프로필, 키워드 로고, 컬렉션 대표 이미지, 작품별 커스텀 이미지는 모두 WebP 업로드를 지원한다.

**[검증 로직]**

1. 콘텐츠 개수 검증 (1~10개)
2. contentId 중복 검증
3. 각 contentId 존재 여부 검증

**[처리 로직]**

1. Collection 엔티티 생성
2. CollectionContent 엔티티 생성 (각 콘텐츠별, 요청 배열 순서를 sortOrder로 저장)
3. CollectionContentImage 엔티티 생성 (작품별 커스텀 이미지, 요청 순서 유지)
4. 컬렉션 키워드 분석 및 저장 (비동기)

---

#### 3.3.2 탐색 세션 조회

`GET /exploration`

**[입력]**

- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 공개 상태의 공개 컬렉션에 포함된 작품만 조회
2. 사용자별 세션 시작 커서와 버전별 작품 30개를 스냅샷으로 저장
3. 각 작품에는 TMDB 줄거리 대신 대표 컬렉션 작성자의 선정 이유(`CollectionContent.reason`)를 반환
4. `PATCH /exploration/progress`로 마지막 열람 위치를 저장하고, 현재 세션이 끝난 경우에만 `POST /exploration/next`로 다음 30개 세션으로 이동

**[세션 규칙]**

- 완전한 30개가 준비된 경우에만 세션을 반환
- 신규 작품이 추가되어도 저장된 진행 중 세션은 교체하지 않음
- 작품 ID, 위치, 제목, 포스터, 연도, 추천 사유, 대표 컬렉션을 세션 스냅샷에 저장
- 비공개·삭제 항목은 조회 즉시 제외하되 다른 작품으로 충원하지 않음
- 같은 위치 재전송은 멱등 성공하고 위치 회귀·건너뛰기는 거부
- 진행 행은 비관적 잠금으로 직렬화하며 세션을 모두 소비해야 다음 세션으로 이동 가능
- DDL 적용 및 smoke test 전에는 `FLINT_EXPLORATION_SNAPSHOT_ENABLED=false` 유지

**[응답]**

- items: 작품 목록 (30개 또는 빈 배열)
- state: IN_PROGRESS, END, EMPTY
- hasNext (Boolean): 다음 30개 세션 존재 여부
- lastViewedPosition (Integer): 마지막으로 확인한 세션 내 위치, 시작 전은 0
- canAdvance (Boolean): 현재 세션을 모두 소비해 다음 세션으로 이동 가능한지
- items[].position (Integer): 세션 내 고정 위치 (1~30)

---

#### 3.3.3 컬렉션 상세 조회

`GET /collections/{collectionId}`

**[입력]**

- collectionId (Long, Path): 컬렉션 ID
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 컬렉션 정보 조회
2. 작성자 정보 조회
3. 포함된 콘텐츠 목록 조회 (sortOrder 순)
4. 요청자의 북마크 여부 확인
5. **최근 본 컬렉션 기록 저장** (RecentViewedCollection upsert)

**[응답]**

- collection: 컬렉션 정보
- author: 작성자 정보. `author.profileImageUrl`은 S3 key를 CloudFront URL로 변환해 반환하고, 외부 URL은 그대로 유지하며 null/빈 이미지는 null로 반환
- contents: 콘텐츠 목록
- isBookmarked (Boolean): 북마크 여부
- isPublic (Boolean): 공개 여부

---

#### 3.3.4 컬렉션 삭제

`DELETE /collections/{collectionId}`

**[입력]**

- collectionId (Long, Path): 컬렉션 ID
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 컬렉션 존재 여부 조회
2. 작성자 권한 확인
3. Collection.deletedAt 기록 (soft delete)

**[응답]**

- 삭제 성공 시 SUCCESS_DELETE

---

#### 3.3.5 최근 본 컬렉션 목록 조회

`GET /collections/recent`

**[입력]**

- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. RecentViewedCollection 테이블에서 최근 조회 기록 조회
2. last_viewed_at 기준 내림차순 정렬
3. `imageList`에는 작품 공식 포스터만 최대 2개 반환

**[응답]**

- collections: 최근 본 컬렉션 목록

---

### 3.4 북마크 (Bookmark)

---

#### 3.4.1 콘텐츠 북마크 토글

`POST /bookmarks/contents/{contentId}`

**[입력]**

- contentId (Long, Path): 콘텐츠 ID
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 해당 콘텐츠의 북마크 존재 여부 확인
2. **북마크 존재**: 삭제 (북마크 해제)
3. **북마크 미존재**: 생성 (북마크 추가)

**[응답]**

- isBookmarked (Boolean): 토글 후 북마크 상태

---

#### 3.4.2 컬렉션 북마크 토글

`POST /bookmarks/collections/{collectionId}`

**[입력]**

- collectionId (Long, Path): 컬렉션 ID
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 사용자 행을 먼저, 컬렉션 행을 다음으로 비관적 쓰기 잠금 조회
2. **북마크 존재**: 삭제 (북마크 해제)
3. **북마크 미존재**: 생성 (북마크 추가)
4. `READ_COMMITTED` 트랜잭션에서 JPA 관계 변경을 flush한 뒤 실제 `collection_bookmark` 행 수를 집계해 `collection.bookmark_count`를 절대값으로 동기화. `INSERT IGNORE`로 무결성 오류를 숨기지 않음
5. 회원 탈퇴 시 사용자 잠금 후 저장한/소유한 컬렉션 ID를 합쳐 오름차순으로 잠금. 북마크 및 연결 데이터 삭제 후 물리 삭제되지 않은 컬렉션(soft-delete 포함)의 카운트를 재계산하고, 연결 데이터 삭제를 flush한 뒤 사용자를 마지막에 삭제
6. `collection_bookmark.user_id`는 `user.id`를 참조하는 `RESTRICT` 외래 키로 보호. 기존 사용자 없는 북마크와 카운트 불일치는 `docs/collection-bookmark-integrity.sql`로 점검 배포 중 정리하며, 외래 키 DDL은 DML commit 이후 별도로 적용

**[응답]**

- isBookmarked (Boolean): 토글 후 북마크 상태

---

#### 3.4.3 컬렉션 북마크 사용자 목록 조회

`GET /bookmarks/{collectionId}`

**[입력]**

- collectionId (Long, Path): 컬렉션 ID

**[처리 로직]**

1. 읽기 전용 `REPEATABLE_READ` 트랜잭션에서 해당 컬렉션의 북마크 수, 사용자 ID, 사용자 목록을 동일 스냅샷으로 조회
2. 북마크 수는 실제 관계 수 기준. 유효한 기존 사용자 전체를 반환하며 사용자 상태에 따른 추가 필터나 목록 개수 제한을 적용하지 않음
3. 저장 사용자 전체의 프로필 이미지 S3 key를 CloudFront URL로 변환

**[응답]**

- userList: 북마크한 사용자 목록. `profileImageUrl`은 CloudFront URL로 반환하고, 외부 URL은 그대로 유지하며 null/빈 이미지는 null로 반환
- bookmarkCount (Integer): 총 북마크 수

---

### 3.5 콘텐츠 (Content)

---

#### 3.5.1 콘텐츠별 OTT 목록 조회

`GET /contents/ott/{contentId}`

**[입력]**

- contentId (Long, Path): 콘텐츠 ID
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 해당 콘텐츠의 `KR.flatrate`로 동기화된 활성 OTT 전체 조회
2. 사용자 구독 여부와 무관하게 `displayPriority`, provider ID 순으로 반환
3. 비활성 provider는 제외

**[응답]**

- otts: OTT 목록
    - ottId (Long): 내부 OTT provider ID
    - name (String): OTT 이름
    - logoUrl (String): 로고 URL

> TMDB Watch Provider 데이터는 JustWatch 제공 데이터이므로 이를 사용하는 클라이언트 화면에 JustWatch 출처를 표시한다.

---

#### 3.5.2 북마크한 콘텐츠 목록 조회

`GET /contents/bookmarks`

**[입력]**

- Authorization Header: Bearer {accessToken}
- cursor (Long, optional): 이전 응답의 nextCursor
- size (Integer, default: 10): 조회 개수 (1~50)

**[처리 로직]**

1. 사용자가 북마크한 콘텐츠 목록을 북마크 최신순으로 조회
2. size + 1개를 조회해 다음 페이지 존재 여부 판단
3. 콘텐츠 상세 정보와 cursor 페이지네이션 메타 포함하여 반환
4. 현재 사용자의 `content_bookmark` 관계만 반환하며 다른 사용자 작품과 미저장 작품을 병합하지 않음

**[응답]**

- data: 북마크한 콘텐츠 목록
    - author (String, nullable): 영화 감독 또는 TV creator. 없으면 `null`
- meta.type: CURSOR
- meta.returned: 현재 응답의 콘텐츠 수
- meta.nextCursor: 다음 페이지 조회용 cursor

---

#### 3.5.3 북마크한 콘텐츠 개수 조회

`GET /contents/bookmarks/count`

**[입력]**

- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 사용자가 북마크한 전체 콘텐츠 수 조회

**[응답]**

- totalCount (Integer): 사용자가 북마크한 전체 콘텐츠 수

---

#### 3.5.4 콘텐츠 검색 (DB)

`GET /contents/search`

**[입력]**

- keyword (String, optional): 한글 또는 영문 검색어. NFKC·소문자·문자와 숫자 외 제거 후 유니코드 코드포인트 기준 최소 1자. 포함 검색 활성화 시 1자는 정규화 제목 LIKE 검색, 비활성화 시 전체 일치만. 2자 이상은 기존 검색. 미입력·빈 문자열·공백만 입력하면 검색어 필터 없음
- genre (SearchGenre, optional): 단일 장르 필터
- mediaType (MOVIE/TV, optional): 미디어 타입
- cursor (String, optional): 이전 응답의 opaque cursor
- size (Integer, default: 20): 조회 개수

**[처리 로직]**

1. 검색어의 공백·특수문자·이모지와 대소문자를 정규화
2. `title_ko`, `title_en`, 정규화 제목을 합친 `search_title` ngram FULLTEXT 검색
3. 검색어가 있으면 정규화 완전 일치, FULLTEXT 관련도, 콘텐츠 ID 내림차순으로 정렬하며 인기순은 적용하지 않음
4. 검색어가 없으면 북마크 수, 콘텐츠 ID 내림차순으로 정렬
5. 검색어·단일 장르·미디어 타입 사이는 AND. 장르 필터는 해당 장르를 포함한 작품을 조회하며 작품 자체의 다중 장르 관계는 유지
6. 정렬 모드·완전 일치 등급·관련도·ID 또는 북마크 수·ID를 담은 버전형 cursor로 페이지 이동
7. 기존 `genre=ACTION&genre=ROMANCE` 또는 `genre=ACTION,ROMANCE` 입력은 첫 값인 `ACTION`만 적용하며 이후 값은 검증하지 않음
8. 장르 미입력 또는 첫 값이 비어 있으면 장르 필터를 적용하지 않고, 첫 값이 유효하지 않으면 400 반환
9. 정규화 후 문자와 숫자가 없는 입력은 `INVALID_INPUT`(400), `keyword는 문자 또는 숫자를 1자 이상 포함해야 합니다.`로 거절한다. 포함 검색 활성화 시 1자는 한글·영문 정규화 제목의 LIKE 포함 조건으로 조회하며 전체 일치(등급 0) 우선·부분 일치(등급 1)·ID 내림차순이다. FULLTEXT·관련도 계산을 하지 않고 점수는 0이다. 비활성화 시 기존 제목 전체 일치만 지원한다. 2자 이상은 기존 매칭·정렬을 유지한다.
10. 검색어 없는 장르 조회는 기존 `(genre_id, content_id)` 인덱스에서 최대 2,049개 관계만 검사해 접근 경로를 선택한다. 관계가 2,048개 이하이면 장르 작품부터 읽어 인기순으로 정렬하고, 그보다 많으면 기존 인기순 작품 인덱스부터 읽고 장르 관계를 확인한다. 경로 선택용 LIMIT은 결과 후보를 잘라내지 않는다. 미디어 타입·인기순 v1 커서·size + 1 계약은 유지한다.
11. 검색어 있는 조회는 완전 일치 ID와 FULLTEXT ID를 각 인덱스로 조회해 UNION으로 중복 제거한 뒤 장르·미디어 타입·커서를 적용하고 기존 순서대로 `size + 1`개 조회. 개별 후보 조회에는 LIMIT을 적용하지 않음
12. 작품 제목 검색은 `content_search_document`로 통일한다. `localized-search-enabled`와 `search-read-model-enabled` 전환 플래그 및 원본 `title/search_title` FULLTEXT 조회 경로는 제거하며 기존 Parameter Store 값이 남아 있어도 읽기 경로를 전환하지 않는다. 제목 재가공·백필은 수행하지 않는다.
13. 수동 인덱스 DDL은 `docs/content-search-performance-ddl.sql`, 적용·검증·복구 절차는 `docs/content-search-performance.md`. 새 완전 일치 인덱스를 확인한 뒤 API를 배포하며 운영 DB 적용·배포·프론트엔드 수정은 로컬 구현 범위에 포함하지 않음
14. `LOWER(title)` 함수형 인덱스는 점검 시간에 `ALGORITHM=INPLACE LOCK=SHARED`로 생성하며 작업 중 쓰기가 차단됨. 희소·빈 장르는 인기순 인덱스를 많이 읽어 기존보다 느릴 수 있으므로 운영 배포 전 해당 조건도 검증
15. ngram 경로는 정규화 검색어를 코드포인트 기준 2글자 토큰의 필수 AND로 변환하고 정규화 한글/영문 제목의 연속 일치를 추가 검증한다. 현재 read model의 FULLTEXT 점수·정확 일치→관련도→ID 정렬을 유지한다. 한글 keyword v3·영문 단어 v4·한 글자 포함 v5를 사용하고 구형 keyword v1/v2는 거절한다. 인기순은 항상 v1이다.
16. `/search/contents`는 같은 native 후보·정렬을 재사용하며 keyword 배열을 제한하지 않는다. `/search/bookmarked-contents`는 검색어가 있을 때 `content_search_document`와 연결해 제목 조건을 검사하고 원본은 응답 필드 조회에만 사용한다. 검색어가 없으면 기존 저장 목록을 조회한다. 사용자 조건·북마크 ID 내림차순·숫자 커서·size+1 및 최소 입력 규칙은 유지한다. 사용하지 않는 구형 조회 메서드와 Hibernate FULLTEXT 함수 등록은 제거한다.
17. 장르 식별은 `genre.code`이며 표시명과 영문 별칭은 식별자가 아니다. 24개 표준 장르를 유지하고 `Action & Adventure → ACTION`, `Sci-Fi & Fantasy → SCIENCE_FICTION`, `War & Politics → WAR`로 연결한다. 기존 6개 검색 필터 계약은 유지한다.
18. 배치는 Movie/TV별 TMDB 장르 ID를 `tmdb_genre_mapping`으로 연결하며 이름으로 마스터를 생성하지 않는다. 미등록 ID는 해당 작품을 RETRY 처리하고 기존 콘텐츠·장르·OTT를 보존한다. 관리자 genreNames는 표준명·고정 코드·등록 별칭만 허용하며 미등록 값은 400이다.
19. 수동 이관 SQL은 `docs/content-search-genre-normalization.sql`이며 원본 백업·표준 ID 선택·500개 콘텐츠 단위 관계 병합·기본 ROLLBACK·명시적 복구를 지원한다. 모든 쓰기를 중지한 점검 적용이며 언어/중복 작품 삭제나 사용자 취향 재분석을 수행하지 않는다.
20. `contentSearchDocumentBackfillJob`은 수동 옵션으로만 등록·실행한다. 기존 ko/en 제목으로 파생 검색 필드만 500건 chunk·ID 체크포인트로 재생성하고 제목 없는 행이 있으면 시작을 거절한다. 같은 backfillVersion으로 실패 실행을 재시작하며 실행 중 해당 프로세스의 TMDB 시작/자동 재개를 차단한다.
21. 검색 인덱스는 검색용 테이블에서 ngram 크기 2와 빈 제목 전용 stopword 테이블을 사용한다. 인덱스 재생성은 별도 수동 점검 작업이며 글로벌 stopword는 자동 변경하지 않는다. 원본의 FULLTEXT 세 개는 통일 코드 배포 후 명시적 승인 범위로 제거하고 재생성하지 않는다.
22. 검색·장르 운영 이관은 모든 쓰기를 중지한 한 번의 점검으로 진행한다. 장르 원본/관계/매핑/컬럼·인덱스 정의를 DB 밖으로 내보내고, 기존 ENUM NOT NULL 코드는 VARCHAR(32) NULL로 전환한 뒤 명시적 이름 별칭으로 재산정한다. BACKED_UP/PREPARED/RELATIONS_DONE/FINALIZING/COMPLETED 단계를 기록하고 실패한 단계만 재개하며 원본 백업을 덮어쓰지 않는다. 최종 단계만 NOT NULL·UNIQUE를 적용하고 복원은 이번 실행 소유 UNIQUE만 제거한다.
23. 운영 dev 프로필은 유지하되 Parameter Store에 spring.jpa.hibernate.ddl-auto=none을 명시한다. flint.batch.execution-enabled는 기본 true이며 false일 때 관리자 시작은 기존 409로 거절하고 자동 재개·heartbeat는 중지하되 graceful 종료 정리는 유지한다. 점검 중 실행·자동 스케줄·S3 정리·관리자 지표 스케줄을 끄고 검증 후 순차 활성화한다.
24. 점검 실패·장시간 실행 시 SQL 종료를 확인하고 저장된 단계/동일 backfillVersion부터 재개한다. 자동 원복·구버전 재기동·작품 삭제·사용자 관계 정리·전체 import·RDS 스냅샷은 수행하지 않는다. 기호 전용 원문은 nullable 정규화 결과를 유지한다. 백필의 원본 제목은 NULL-safe 바이너리 비교로 대소문자·악센트·끝 공백 변경까지 검출하고 해당 chunk를 rollback한다. 배치 재활성화 실패 시 배치만 다시 중지하고 이미 검증한 API는 유지한다.
25. 이전 이관에서 MySQL 8.4.8의 기존 title FULLTEXT가 있을 때 search_title만 재생성해도 테이블 공통 stopword 설정이 남는 현상을 확인했다. 당시 별도 승인된 COPY로 기존 정의·데이터·관계를 보존하고 새 연결의 부분 매칭을 검증했다. 이는 과거 이관 기록이며 현재 통일 API의 원본 인덱스 재생성·COPY·localized flag 복구 절차가 아니다. 현재 변경·복구는 27번의 통일 runbook을 따른다.
26. 필수 검색 전용 read model `content_search_document`는 작품 ID·유형·정규화 제목·기존 검색 문서를 동일하게 복제한다. 독립 AUTO_INCREMENT FTS_DOC_ID는 작품 ID와 분리하며 삭제된 문서 ID를 재사용하지 않는다. INSERT/UPDATE/DELETE 트리거로 원본과 같은 트랜잭션에서 동기화하고 북마크 수 등 검색 외 변경은 문서를 다시 쓰지 않는다. 설치·백필이 필요한 환경은 통일 API 배포 전에 검색 문서·인덱스·일치·orphan 검증을 완료한다. 이번 dev 통일에서는 기존 문서를 보존하고 백필하지 않는다.
27. read model은 좁은 검색 문서에서 연속 일치·장르·유형·커서·정렬을 적용한 뒤 최종 페이지만 원본 작품과 연결한다. 문서 ID·점수 단독 조회가 covering이어도 실제 결합 쿼리가 covering인지는 별도로 실행계획에서 검증해야 한다. 완전 일치→관련도→ID, 한글 v3·영문 v4·한 글자 v5 및 인기순 v1·저장 작품 숫자 커서를 유지한다. 무제한 keyword 결과 배열 계약도 유지한다. 원본 FULLTEXT와 플래그 기반 읽기 복구 경로는 폐기하며 RDS 사양은 변경하지 않는다. 통일·인덱스 제거·재기동·복구 절차는 `docs/content-fulltext-retirement.md`를 따른다. 이후 작품 정리 SQL의 트리거 검사는 기존 세 트리거를 명시적으로 인식해야 한다.
28. 검색어 없는 장르 인기순의 빈번 장르 경로는 인기 인덱스의 ID·저장 수 covering 조회에서 장르·유형·커서·LIMIT을 먼저 적용한 뒤 상세 필드를 읽는다. 희소·빈 장르는 10번의 bounded probe로 장르 선행 경로를 선택해 전체 인기 인덱스 순회를 피한다. 두 경로는 동일한 인기순 정렬과 v1 커서를 사용하며 캐시나 검색 제한을 추가하지 않는다. 현재 외부 API의 6개 장르는 운영 데이터상 모두 경계값보다 크므로 이번 개선이 이들의 첫 페이지나 키워드 검색을 해결했다고 간주하지 않는다.
29. 현재 인프라에서 키워드 후보 조회 PoC는 세션 임시 테이블로만 검증한다. 토큰별 FULLTEXT 점수를 합산한 실험은 일부 순서·점수가 달라 운영 경로에 반영하지 않으며, 단일 FULLTEXT 경로도 일관된 성능 개선이 없으면 채택하지 않는다. 관련도 계산·커서 계약 변경은 별도 확인이 필요하다. 기능 검증과 성능 검증을 구분하며, 잔여 지연을 확인하고 사용자가 해당 상태의 서비스 재개를 명시적으로 요청한 경우에만 최신 검증 이미지의 내부·외부 health와 API 응답을 확인해 점검을 해제한다. 이를 키워드 성능 최적화 완료로 기록하지 않는다.

30. `flint.content.english-word-search-enabled`는 기본 false다. true이면 정규화된 입력이 ASCII 영문·숫자·공백만 포함하고 영문 문자가 하나 이상일 때 단어 검색으로 전환한다. 전체 단어가 입력 순서대로 인접한 구문만 일치하며 접두어·단어 중간 문자열·임의 띄어쓰기는 지원하지 않는다. 한글·혼합·숫자만 입력은 기존 ngram 경로다.
31. 단어 정규화는 NFKC·소문자·문자/숫자/공백 외 제거·공백 통합·trim이다. 검색 경로는 공백 제거 정규화 결과의 코드포인트 수로 구분한다. 1자는 포함 검색 플래그에 따라 LIKE 검색 또는 기존 전체 일치이며, 2자 이상 영문은 기존 단어·구문 검색이다. `harry potter`는 허용하지만 `harrypotter`, `ha rry pot ter`, `arry`, `harry pot`, `potter harry`는 Harry Potter와 일치하지 않는다. 다른 작품의 실제 제목이 해당 입력과 일치하면 그 작품은 반환할 수 있다.
32. `content_search_document`의 nullable `word_title_ko`, `word_title_en`을 기본 파서 FULLTEXT로 인덱싱한다. 원본의 같은 컬럼은 동기화 값으로 유지하되 FULLTEXT는 제거한다. `innodb_ft_min_token_size=1`과 빈 제목 전용 stopword 목록으로 `it`와 구문 내부의 `a`·`i`도 저장한다. 1자 입력에서는 이 FULLTEXT 인덱스를 조회하지 않는다. 포함 검색 활성화 시 한 글자 커서만 v5이고, 2자 이상 영문 v4·한글 v3·인기 v1은 유지한다. 제목 쓰기·배치·분류 결과 반영에서 공통 정규화를 사용하고 트리거로 문서를 동기화한다.
33. 단어 검색의 완전 일치는 기존 정규화 제목 인덱스로 후보를 좁힌 뒤 공백을 보존한 제목도 비교한다. FULLTEXT 후보와 UNION하며 제목별 단어 경계·연속 구문을 최종 확인한다. 정확 일치→단어 관련도→ID 순서를 사용하고 개별 후보 LIMIT이나 bigram fallback은 없다. 영문 keyword cursor는 v4이며 이전 버전은 400으로 거절한다. 한글 read model v3·인기 v1·저장 작품 숫자 커서는 유지한다.
34. 세 작품 검색 API가 같은 단어 매칭 정책과 검색 문서를 사용한다. 저장 작품은 native SQL·interface projection으로 조회하며 사용자 조건·최신 저장순·숫자 커서를 유지한다. `/search/contents` keyword 결과는 무제한 배열이다. 단어 관련도 점수와 순서는 기존 bigram과 다를 수 있다.
35. 영어 검색 기동 검증은 원본·검색 문서의 동기화 컬럼과 기존 트리거·토큰·빈 stopword 설정, 검색용 ngram/default-parser FULLTEXT를 확인한다. 원본 FULLTEXT는 요구하지 않으며 구형 localized/read-model 플래그 의존성은 없다. 필요한 문서 백필은 기존 Job의 승인된 수동 기능을 사용하며 이번 dev 변경에서는 실행하지 않는다. 수동 SQL `docs/content-english-word-search.sql`도 원본 FULLTEXT를 재생성하지 않는다.
36. 한 글자 부분 검색은 기존 `content_search_document.normalized_title_ko/normalized_title_en`의 `LIKE '%문자%'`를 사용한다. 전체 일치와 부분 일치를 별도 조회하며 중복은 NULL-safe 전체 일치 제외 조건으로 방지한다. 정규화 후 문자는 `utf8mb4_0900_bin`으로 비교해 악센트를 다른 문자로 취급한다. 문자 역색인·신규 테이블·인덱스·트리거·백필·추가 인프라는 필요 없다.
37. `flint.content.single-character-infix-search-enabled`는 기본 false이며 필수 검색 문서에서 동작한다. 메인 검색은 전체 일치 우선·부분 일치 ID 역순으로 필터 적용 후 size+1개를 확보하고 최종 페이지만 원본에서 읽는다. v5 커서는 이전 한 글자 커서와 호환되지 않으며 비활성화 시 v5도 거절한다. LIKE 자체는 B-tree 범위를 좁히지 못해 드문 문자·없는 결과·희소 필터에서는 대량 스캔이 가능하다.
38. 이전 문자 역색인 SQL·문자 전용 백필은 대안 기록이며 현재 LIKE 적용에는 실행하지 않는다. 기존 정규화 검색 문서가 준비되어 있으면 코드 배포와 읽기 플래그 활성화만 필요하며 `ddl-auto=none`을 유지한다. 운영 성능 검증은 시간 제한을 둔 읽기 전용 쿼리로 수행하고 API 배포·설정 변경과 구분한다. 2자 이상 검색 정책·정렬·커서는 유지한다.
39. 한 글자 LIKE 배포 준비에서는 수동 이관·설치 SQL 파일에 의존하는 테스트를 실행 대상에서 제거하고, 자체 fixture를 사용하는 검색·커서·사용자 격리 회귀 테스트는 유지한다. 운영 `dev` 프로필의 Parameter Store에서 `single-character-infix-search-enabled=true`를 설정하며 다음 API 기동부터 적용한다. 백필 비활성·`ddl-auto=none`·기존 2자 이상 검색 플래그를 유지하고 신규 DDL·백필·추가 인프라 없이 배포한다. 수동 SQL 자체의 설치·복원 검증은 자동 테스트 범위에서 제외된다.
40. 2026-10-14 dev에 통일 API를 배포하고 원본 FULLTEXT 세 개를 INPLACE/LOCK=NONE으로 제거했다. 삭제 후 재기동·health·공개 검색 커서, 실제 Repository 결과 및 946,275건의 데이터·관계·트리거 보존을 확인했다. 신규 테스트·스위트 실행·인증 사용자 HTTP 북마크 조회·부하는 검증하지 않았으며 상세 결과·롤백 제한은 `docs/content-fulltext-retirement.md`에 기록한다.
41. 검색 회귀 테스트는 통일 read model 기준의 한·영 제목 일치·중복 제거와 keyword v3 커서를 검증한다. 실행계획 테스트의 전용 MySQL 컨테이너에는 검색 문서 동기화 트리거 생성을 위한 `log-bin-trust-function-creators=1`을 설정하며 운영 DB 설정은 변경하지 않는다.

검색 정렬의 비용, 운영/fixture 측정 구분 및 미채택 개선 후보는 `docs/bookmark-order-search-performance.md`에 정리한다. 이 문서는 조사 기록이며 현재 정렬·API·운영 정책을 변경하지 않는다.

**[응답]**

- data: 검색 결과 목록
- meta.type: CURSOR
- meta.nextCursor: 다음 페이지용 opaque cursor

---

### 3.6 검색 (Search)

---

#### 3.6.1 콘텐츠 검색 (레거시)

`GET /search/contents`

**[입력]**

- keyword (String, optional): 작품 제목 검색어. 정규화 후 1자는 포함 검색 활성화 시 정규화 제목 LIKE 검색·전체 일치 우선·ID 내림차순으로 최대 50개, 비활성화 시 전체 일치만. 2자 이상은 기존 무제한 검색이며 미입력·공백은 기본 인기 목록 30개

**[처리 로직]**

1. 검색어가 있으면 메인 검색의 native 완전 일치·FULLTEXT 후보 UNION을 재사용. localized=true에서는 정규화 한글/영문 제목과 search_title로 검색하고 완전 일치·관련도 내림차순·ID 내림차순으로 정렬
2. 키워드 검색 결과는 개수 제한 없이 반환
3. 검색어가 없으면 북마크 수 내림차순, 콘텐츠 ID 내림차순으로 30개 반환
4. 기본 목록 정렬은 `idx_content_popular(bookmark_count DESC, id DESC)` 인덱스 순서와 일치

**[응답]**

- contents: 검색 결과 목록

**[현재 제한 및 클라이언트 책임]**

- 콘텐츠 원산지/제작 국가 필드가 없어 국내 작품 비율을 서버에서 보장할 수 없음. 고정 비율이 필요하면 국가 메타데이터 또는 온보딩 큐레이션 테이블이 필요함
- FULLTEXT 관련도 정렬은 지원하지만 철자 유사도 비교나 오타 자동 보정은 제공하지 않음. 호환 계약상 keyword 결과는 무제한 배열이며 커서가 없음
- 검색어 삭제 즉시 기본 목록 복원, 요청 타임아웃, 오류 팝업은 클라이언트 상태 및 네트워크 처리 영역

> 신규 화면은 `GET /contents/search`를 사용한다. `GET /search/contents`는 호환을 위한 deprecated API다.

---

#### 3.6.2 북마크 컬렉션 검색

`GET /search/bookmarked-collections`

**[입력]**

- keyword (String): 검색어
- cursor (Long, optional): 페이지네이션 커서
- size (Integer, default: 20): 조회 개수
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 사용자가 북마크한 컬렉션 중 검색
2. 컬렉션 제목으로 LIKE 검색
3. Cursor 기반 페이지네이션 적용

**[응답]**

- items: 검색 결과 목록
- nextCursor (Long): 다음 페이지 커서
- hasNext (Boolean): 다음 페이지 존재 여부

---

#### 3.6.3 북마크 콘텐츠 검색

`GET /search/bookmarked-contents`

**[입력]**

- keyword (String, optional): 작품 제목 검색어. 정규화 후 1자는 포함 검색 활성화 시 정규화 제목 LIKE 검색, 비활성화 시 전체 일치만. 2자 이상은 기존 검색이며 미입력·공백은 저장 목록. 사용자 조건·최신 저장순·북마크 ID 커서는 유지
- cursor (Long, optional): 페이지네이션 커서
- size (Integer, default: 20): 조회 개수
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 사용자가 북마크한 콘텐츠 중 검색
2. 기존 QueryDSL 경로에서 localized=true이면 정규화 완전 일치 또는 등록된 Boolean FULLTEXT 함수로 search_title 검색. false에서는 기존 제목 LIKE 경로로 복구. 감독은 검색 대상이 아님
3. 북마크 ID 내림차순(최신 저장순)과 숫자 Cursor 기반 페이지네이션 유지. 전체 카탈로그 후보 조회를 선행하지 않음

**[응답]**

- items: 검색 결과 목록
- nextCursor (Long): 다음 페이지 커서
- hasNext (Boolean): 다음 페이지 존재 여부

---

### 3.7 홈 (Home)

---

#### 3.7.1 추천 컬렉션 조회

`GET /home/recommended-collections`

**[입력]**

- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 사용자의 취향 키워드 조회
2. 공개 컬렉션 중 후보 선정
3. Fliner(컬렉션 작성자)의 취향 키워드와 사용자 키워드 일치율 계산
4. 일치율 기준 상위 컬렉션 반환

**[추천 알고리즘]**

```
일치율 = (공통 키워드 수 / 전체 키워드 수) × 100
```

**[응답]**

- collections: 추천 컬렉션 목록

---

#### 3.7.2 인기 컬렉션 조회

`GET /home/popular-collections`

- 총 북마크 수가 1개 이상인 컬렉션만 노출하며, 조건을 만족하는 컬렉션이 10개 미만이면 해당 개수만 반환
- 컬렉션별 `imageList`에는 작품 공식 포스터만 최대 2개 반환
- 각 항목에 총 `bookmarkCount`를 포함
- 유효한 USER 토큰이면 실제 저장 관계 기준 `isBookmarked`, 토큰이 없으면 `false` 반환
- 만료·위조·정지 계정 토큰은 공개 API라도 기존 인증 오류 반환
- 저장 상태는 응답 대상 컬렉션 ID에 대해 한 번에 조회하며 사용자별 응답이므로 공유 캐시하지 않음
- 사용자 업로드 작품 소개 이미지는 카드 미리보기에 포함하지 않음

---

### 3.8 관리자 및 TMDB 카탈로그 운영

#### 3.8.1 플랫폼 관리자 API

- 모든 관리자 API는 플랫폼 애플리케이션의 `/api/v1/admin/**`에서 제공한다.
- 기존 관리자 URL·요청·응답 형식은 유지하고 API 호스트만 `https://flint.r-e.kr`로 통합한다.
- 관리자 인증은 일반 사용자 인증과 분리된 `ADMIN` audience JWT와 `AdminPrincipal`을 사용한다.
- `/api/v1/admin/**`는 우선순위 1 SecurityFilterChain, 일반 API는 우선순위 2 SecurityFilterChain으로 처리한다.
- 관리자 로그인·refresh 외 요청은 ADMIN 토큰만 허용하고, 일반 API는 USER 토큰만 허용한다.
- `admin.token_valid_after` 이전에 발급된 ADMIN Access/Refresh Token은 거부해 전환 시 전원 재로그인한다.
- `apps/admin-api`, 관리자 전용 배포 workflow/script 및 EC2/EIP/SG/ECR/IAM/DNS는 폐기하고 관리자 프론트 S3·CloudFront는 유지한다.

#### 3.8.2 TMDB 실행 조정

- 일간 업무 키는 `DAILY:yyyy-MM-dd`, 월간 업무 키는 `MONTHLY:yyyy-MM`, 분류 전용 키는 `CLASSIFY_ONLY:yyyy-MM`이다.
- DB lease를 획득한 단일 인스턴스만 실행한다. 같은 업무 키는 기존 실행을 반환하고 다른 업무가 실행 중이면 409를 반환한다.
- 일간 동기화는 05:00 KST, 월간 조정은 매월 1일 02:00 KST에 시작한다. 자동 스케줄은 dev에서만 활성화하고 prod는 검증 전까지 비활성화한다.
- TMDB 상세 요청은 동시성 3, 전역 5req/s, chunk 50으로 제한한다.
- changes reader는 페이지와 항목 위치를 ExecutionContext에 저장하고 요청 날짜 범위는 최대 14일이다.
- 종료 시 실행 중 Job에 STOP을 요청하고 최대 5분간 chunk checkpoint 완료를 기다린다. 새 인스턴스는 같은 JobInstance를 checkpoint부터 재개한다.
- 상세 조회는 `translations,credits,watch/providers`를 한 번에 받고 콘텐츠·장르·`KR.flatrate` OTT 관계를 같은 chunk에서 멱등 upsert/reconciliation한다.
- TMDB에 포스터가 없는 콘텐츠는 `content.poster=NULL`로 보존하며 임의 이미지나 placeholder URL을 원본 데이터로 저장하지 않는다. 표시용 placeholder는 클라이언트가 처리한다.
- Movie/TV provider master는 `watch_region=KR` 결과의 합집합으로 동기화하며 누락 provider는 삭제하지 않고 비활성화한다.
- 기존 6개 provider는 명시적 이름 alias로 TMDB provider ID를 연결해 사용자 구독 FK를 보존한다.
- 월간 export는 유효 ID registry를 갱신하되 신규·PENDING·RETRY·갱신기한 경과 ID만 상세 조회한다.
- 일간 작업은 `next_refresh_at` 만료 대상을 나눠 처리해 최초 동기화 후 약 30일 주기로 제목과 OTT를 갱신한다.

#### 3.8.3 제목 분류와 데이터 정리

- 제목 선택 우선순위는 `ko-KR → 기타 ko → en-US → en-GB → 기타 en`이며 원어가 ko/en이면 유효한 원제도 후보로 인정한다.
- 성공한 TMDB 상세 응답에 한글·영문 제목이 모두 없으면 `INELIGIBLE_LANGUAGE`로 분류한다.
- 404는 `NOT_FOUND`, 429·5xx·timeout 등 일시 오류는 `RETRY`로 기록하며 기존 콘텐츠를 삭제하지 않는다.
- `CLASSIFY_ONLY` 실행은 registry만 변경하고 콘텐츠·장르·OTT 관계는 변경하지 않는다.
- 분류 중 확인한 한글·영문 제목과 정규화 검색 문자열은 registry에 staging하고, cleanup 완료 시 남은 콘텐츠에 일괄 반영한다.
- cleanup preview는 현재 콘텐츠의 PENDING/RETRY/미등록 상태가 0일 때만 후보 ID·건수·SHA-256 해시를 고정한다.
- cleanup execute는 preview manifest와 해시가 일치하고 운영자가 RDS 수동 스냅샷 생성을 확인한 경우에만 실행한다.
- cleanup execute는 `PREVIEW` 또는 중단된 `EXECUTING` manifest를 동일 해시로 재개할 수 있다.
- 500건 트랜잭션으로 `collection_content_images → collection_content → content_bookmark → ott_content → content_keywords → content_genre → content` 순서로 삭제한다.
- 삭제 후 작품 순서를 재정렬하고 빈 컬렉션은 soft-delete하며 컬렉션 키워드와 추천 Redis 캐시를 갱신한다.
- `collection/content/` S3 key만 대기열에 기록하고 30일 뒤 별도 정리 스케줄에서 삭제한다.
- 수동 DDL과 운영 점검 SQL은 `docs/tmdb-catalog-platform-consolidation.sql`을 기준으로 한다.

**[완료 조건]**

- 언어 부적격 콘텐츠 0건
- 모든 콘텐츠에 `title_ko` 또는 `title_en` 중 하나 이상 존재
- 콘텐츠 연결 테이블 orphan 0건
- 활성 빈 컬렉션 0건
- 동일 업무 키 중복 실행 0건

#### 3.8.4 제목 기준 수동 중복 정리

- 수동 SQL은 `docs/content-title-deduplication.sql`에 작성하며 운영 DB에는 자동 적용하지 않는다.
- 영화/TV 유형과 연도를 구분하지 않고 `LOWER(TRIM(title))`을 바이너리 비교하여 동일 제목을 묶는다. 앞뒤 공백과 대소문자만 무시하며 악센트·내부 공백·특수문자는 유지하고, NULL/빈 제목은 제외한다.
- 현재 `bookmark_count`가 가장 큰 작품을 남기며 동점이면 작품 ID가 가장 작은 행을 유지한다.
- 삭제 대상의 사용자 북마크를 대표 작품으로 이관한다. 같은 사용자의 중복 저장은 북마크 ID가 가장 큰 1건을 유지하고 대표 작품의 저장 수를 실제 관계 수로 재계산한다.
- 컬렉션 작품은 행 ID를 유지하고 작품 ID만 이관하여 소개 글·스포일러·순서·기존 커스텀 이미지와 이미지 관계를 보존한다.
- 같은 컬렉션에 동일 제목 그룹의 작품이 둘 이상 있으면 자동 병합하지 않고 전체 변경을 차단한다. 소개 글·이미지 보존 정책을 별도로 확인한 후 진행한다.
- OTT·장르·작품 키워드는 대표 작품의 데이터만 유지한다. 삭제 작품의 공식 메타데이터를 대표 작품에 합치지 않는다.
- 탐색 스냅샷과 TMDB registry/prune 기록은 이력으로 유지한다. 삭제 작품의 스냅샷 항목은 기존 탐색 노출 정책에 따라 숨겨질 수 있다.
- 기본 실행은 조회 전용이며 DML 리허설도 `ROLLBACK`으로 끝난다. 점검 중 모든 API·배치 쓰기를 중단하고 연결 구조·충돌·사후 검증을 확인한 동일 연결에서만 명시적으로 commit한다.
- SQL은 1회성 정리이며 삭제된 TMDB ID의 재수집을 차단하지 않는다. 배치 재개 전 수집 정책과 추천 캐시 무효화 및 영향받은 사용자/컬렉션의 취향 재계산을 별도로 검토한다.

#### 3.8.5 TMDB 배치 수집 허용 정책

- 제작 국가나 원어와 무관하게 한글/영문 번역 제목 또는 ko/en 원제가 확인되는 작품만 수집한다. 표시 제목은 한글 우선, 없으면 영문이다.
- 표시 제목의 `LOWER(TRIM(title))` 바이너리 값, 영화/TV 유형, 양수 연도가 모두 같으면 중복이다. 내부 공백·악센트·특수문자는 구분하며 연도 0/NULL/음수는 중복 검사에서 제외한다.
- 월간 import, 일간 changes, 주기적 refresh의 공통 저장 단계에서 검사한다. DB의 기존 작품을 신규 후보보다 우선하며 동일 chunk의 신규 중복은 Reader 순서상 첫 작품만 적재한다.
- 신규 중복은 콘텐츠·장르·OTT를 적재하지 않는다. 기존 작품의 제목만 변경되어 충돌하면 모든 제목/검색 컬럼을 보존하고 다른 메타데이터·장르·OTT는 갱신한다. 연도 변경을 포함한 충돌이면 해당 작품의 전체 갱신을 보류한다.
- 중복 제외/갱신 보류는 registry에 `DUPLICATE_TITLE`과 사유를 기록하고 30일 뒤 또는 changes 감지 시 재검사한다. 충돌이 해소되면 정상 수집할 수 있다.
- 제목만 보존한 정상 갱신은 registry에도 실제 보존한 제목을 기록한다. CLASSIFY_ONLY는 콘텐츠를 변경하지 않으며 후속 제목 반영도 같은 중복 검사를 거친다.
- generated `title_dedup_key`와 `(media_type, year, title_dedup_key)` 일반 인덱스로 chunk 단위 조회한다. `tmdb_sync_lock.TMDB_CONTENT_WRITE` 영구 행 잠금과 locking read로 배치/제목 반영을 직렬화하고 오래된 스냅샷을 피한다.
- export 관측의 registry 등록은 짧은 별도 트랜잭션으로 커밋해 chunk 완료까지 registry 잠금을 유지하지 않는다. 콘텐츠 분류·콘텐츠·장르·OTT 저장은 기존 chunk 트랜잭션에서 함께 커밋/롤백한다.
- 수동 DDL은 `docs/tmdb-batch-admission.sql`이며 컬럼·인덱스·잠금 행이 없으면 배치를 시작하지 않는다. 제목 반영을 포함하는 언어 정리도 삭제 전에 동일한 DDL을 검사한다. 기존 배치와 제목 반영을 중단한 뒤 DDL을 적용하고 신버전 writer만 실행한다.
- 기존 중복 데이터 및 제목만 비교하는 삭제 SQL은 변경하지 않는다. 해당 SQL로 삭제했더라도 유형·연도가 다르거나 연도 미상이면 배치가 재수집할 수 있다. 사용자 연결의 삭제·병합과 운영 SQL 실행은 포함하지 않는다.
- DB 제목 문자 기준 수동 정리는 `docs/content-language-cleanup.sql`로 준비한다. TMDB 분류 완료나 registry 상태와 무관하게 `title`, `title_ko`, `title_en`을 각각 검사하고 한글(자모 포함) 또는 ASCII 영문을 포함하며 다른 언어 문자·결합 악센트가 없는 제목이 하나라도 있으면 작품을 보존한다. 세 제목 모두 허용 기준을 통과하지 못하면서 다른 언어 문자·악센트가 있는 작품만 삭제 후보로 삼는다. 숫자·공백·문장부호·기호·이모지는 언어 판정에서 제외하며 세 제목이 모두 비어 있거나 숫자·기호뿐이면 별도 확인 대상으로 보류한다.
- 위 수동 SQL은 점검 중 모든 API·배치 쓰기를 중단한 상태에서 500건씩 연결 데이터 삭제·순서/컬렉션 키워드 재계산·빈 컬렉션 soft-delete·소유 이미지 30일 삭제 대기열 등록을 수행한다. 전체 삭제 건수·보류 건수·미리보기·이번 chunk는 동일한 임시 판정 결과를 사용하며 기본값은 preview/rollback이다. Redis 추천 캐시 무효화는 별도로 필요하다. 실제 언어나 TMDB 번역 존재 여부를 확인하지 않으므로 DB에 번역이 없으면 정상 작품도 삭제될 수 있고 영문자로 표기된 다른 언어 제목은 남을 수 있다. 기존 배치가 삭제 작품을 재수집할 수도 있다. 관리자 분류·정리 API와 배치 수집 정책은 변경하지 않는다.

---

## 4. 데이터 모델

### 4.1 엔티티 목록

| 모듈 | 엔티티 | 테이블명 | 설명 |
|------|--------|----------|------|
| user | User | users | 사용자 정보 |
| auth | UserIdentity | user_identities | 소셜 로그인 연동 정보 |
| content | Content | contents | 콘텐츠(작품) 메타데이터 |
| content | Genre | genres | 장르 마스터 |
| content | ContentGenre | content_genres | 콘텐츠-장르 매핑 |
| collection | Collection | collections | 사용자 컬렉션 |
| collection | CollectionContent | collection_contents | 컬렉션-콘텐츠 매핑 및 작품 순서 |
| collection | CollectionContentImage | collection_content_images | 컬렉션 포함 콘텐츠별 커스텀 이미지 |
| collection | RecentViewedCollection | recent_viewed_collections | 최근 조회 기록 |
| bookmark | ContentBookmark | content_bookmarks | 콘텐츠 북마크 |
| bookmark | CollectionBookmark | collection_bookmarks | 컬렉션 북마크 |
| taste | Keyword | keywords | 취향 키워드 마스터 |
| taste | ContentKeyword | content_keywords | 콘텐츠-키워드 매핑 |
| taste | UserKeyword | user_keywords | 사용자-키워드 매핑 |
| taste | CollectionKeyword | collection_keywords | 컬렉션-키워드 매핑 |
| ott | OttProvider | ott_providers | OTT 플랫폼 마스터 |
| ott | OttContent | ott_contents | 콘텐츠-OTT 매핑 |
| ott | OttUser | ott_users | 사용자-OTT 구독 정보 |
| batch | TmdbCatalogEntry | tmdb_catalog_entry | TMDB ID 분류 및 갱신 registry |
| batch | TmdbSyncRun | tmdb_sync_run | 안정적 업무 키별 실행 상태와 진행률 |
| batch | TmdbSyncLock | tmdb_sync_lock | 블루·그린 단일 실행 lease |
| batch | TmdbPruneManifest | tmdb_content_prune_manifest | 언어 정리 후보 고정 manifest |
| batch | TmdbS3DeleteQueue | tmdb_s3_delete_queue | 30일 지연 S3 삭제 대기열 |

### 4.2 주요 제약조건

| 테이블 | UNIQUE 제약 |
|--------|-------------|
| user_identities | (provider, provider_user_id) |
| contents | (tmdb_id, media_type) |
| tmdb_catalog_entry | (media_type, tmdb_id) |
| tmdb_sync_run | (run_key) |
| collection_contents | (collection_id, content_id), (collection_id, sort_order) |
| recent_viewed_collections | (user_id, collection_id) |
| content_bookmarks | (user_id, content_id) |
| collection_bookmarks | (user_id, collection_id) |
| content_keywords | (content_id, keyword_id) |
| user_keywords | (user_id, keyword_id) |
| ott_contents | (content_id, ott_provider_id) |
| ott_users | (user_id, ott_provider_id) |

---

## 5. 비기능 요구사항

### 5.1 보안

| ID | 요구사항 |
|----|----------|
| NFR-SEC-001 | JWT 기반 인증 (Access Token: 1시간, Refresh Token: 14일) |
| NFR-SEC-002 | Refresh Token Rotation 적용 (재사용 탐지 시 전체 무효화) |
| NFR-SEC-003 | Access Token Blacklist (로그아웃 시 남은 TTL 동안 차단) |
| NFR-SEC-004 | 비공개 컬렉션은 본인만 조회 가능 |

### 5.2 성능

| ID | 요구사항 |
|----|----------|
| NFR-PERF-001 | 페이지네이션은 Cursor 기반 적용 |
| NFR-PERF-002 | Redis 캐싱 활용 (Refresh Token, Blacklist) |
| NFR-PERF-003 | QueryDSL을 통한 최적화된 쿼리 작성 |

### 5.3 확장성

| ID | 요구사항 |
|----|----------|
| NFR-EXT-001 | 추천 전략 인터페이스 분리 (`CollectionRecommendationPort`) |
| NFR-EXT-002 | 모듈 간 결합도 최소화 (ID 참조만 허용) |
| NFR-EXT-003 | CloudFront 조건부 활성화 지원 |

---

## 6. API 명세 요약

### 6.1 인증 관련

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | /auth/social/verify | 소셜 로그인 검증 | X |
| POST | /auth/signup | 회원가입 | tempToken |
| POST | /auth/refresh | 토큰 갱신 | X |
| POST | /auth/logout | 로그아웃 | O |
| POST | /auth/logout/all | 전체 로그아웃 | O |

### 6.2 사용자 관련

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | /users/nickname/check | 닉네임 중복 확인 | X |
| GET | /users/{userId} | 프로필 조회 | 선택 |
| GET | /users/{userId}/keywords | 취향 키워드 조회 | X |
| GET | /users/{userId}/collections | 작성 컬렉션 목록 | 선택 |
| GET | /users/{userId}/bookmarked-collections | 북마크 컬렉션 목록 | 선택 |
| GET | /users/{userId}/bookmarked-contents | 북마크 콘텐츠 목록 | O |
| PATCH | /users/recalculate/keyword | 키워드 재계산 | O |

### 6.3 컬렉션 관련

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | /collections | 컬렉션 생성 | O |
| GET | /collections | 탐색 목록 | X |
| GET | /collections/{id} | 상세 조회 | O |
| DELETE | /collections/{id} | 컬렉션 삭제 | O |
| GET | /collections/recent | 최근 본 목록 | O |
| GET | /exploration | 현재 탐색 세션 조회 | O |
| PATCH | /exploration/progress | 탐색 진행 위치 저장 | O |
| POST | /exploration/next | 다음 탐색 세션 이동 | O |

### 6.4 북마크 관련

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | /bookmarks/contents/{id} | 콘텐츠 북마크 토글 | O |
| POST | /bookmarks/collections/{id} | 컬렉션 북마크 토글 | O |
| GET | /bookmarks/{collectionId} | 북마크 사용자 목록 | X |

### 6.5 콘텐츠 관련

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | /contents/ott/{id} | OTT 목록 | O |
| GET | /contents/bookmarks | 북마크 콘텐츠 | O |
| GET | /contents/bookmarks/count | 북마크 콘텐츠 개수 | O |
| GET | /contents/search | localized DB 검색 | X |

### 6.6 검색 관련

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | /search/contents | 콘텐츠 검색 | X |
| GET | /search/bookmarked-collections | 북마크 컬렉션 검색 | O |
| GET | /search/bookmarked-contents | 북마크 콘텐츠 검색 | O |

### 6.7 홈 관련

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | /home/recommended-collections | 추천 컬렉션 | O |
| GET | /home/popular-collections | 인기 컬렉션 | 선택 |

### 6.8 관리자 및 배치 관련

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | /admin/auth/login | 관리자 로그인 | X |
| POST | /admin/auth/refresh | 관리자 토큰 갱신 | X |
| POST | /admin/batch/daily-sync | 일간 동기화 요청 | ADMIN |
| POST | /admin/batch/monthly-reconcile | 월간 조정 요청 | ADMIN |
| POST | /admin/batch/language-cleanup/classify | 전체 언어 분류 요청 | ADMIN |
| GET | /admin/batch/runs | 배치 실행 목록 | ADMIN |
| GET | /admin/batch/runs/{runId} | 배치 실행 상세 | ADMIN |
| POST | /admin/batch/language-cleanup/preview | 언어 정리 후보 고정 | ADMIN |
| POST | /admin/batch/language-cleanup/execute | 스냅샷 확인 후 언어 정리 | ADMIN |

---

## 7. 후속 확장 계획

### Sprint 1
- 팔로우/맞팔로우 기능 (`modules:social`)
- 탐색/홈 슬롯 확장

### Sprint 2
- 질문 기능 (`modules:question`)
- 7일 질문 + 24시간 결과 노출

### Sprint 3
- 취향 키워드 3계층 고도화
- 개인화 추천 Stage 1

### Sprint 4
- OpenSearch 도입
- 검색 고도화

### Sprint 5+
- 그래프/벡터/RAG 기반 추천

---

## 부록: 환경 설정

### Profile별 주요 설정

| 설정 | local | dev | prod |
|------|-------|-----|------|
| DB DDL | update | update | none |
| Redis | localhost | localhost | AWS ElastiCache |
| CloudFront | disabled | enabled | enabled |
| JWT Access 만료 | 14d | 14d | 1h |
| JWT Refresh 만료 | 30d | 30d | 14d |
| P6Spy 로깅 | enabled | enabled | disabled |
| TMDB 자동 스케줄 | disabled | enabled | disabled |
| localized title 검색 | flag | flag | flag |
