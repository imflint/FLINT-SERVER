# Flint API 요구사항 명세서

> 최종 업데이트: 2026-06-07
> 버전: MVP 1.0

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
│   └── api                    # REST API 애플리케이션
├── modules/
│   ├── shared                 # 공통 컴포넌트 (Base, Exception, DTO)
│   ├── user                   # 사용자 관리
│   ├── auth                   # 인증/인가
│   ├── content                # 콘텐츠(작품) 관리
│   ├── collection             # 컬렉션 관리
│   ├── bookmark               # 북마크 관리
│   ├── taste                  # 취향 키워드 관리
│   ├── ott                    # OTT 플랫폼 관리
│   └── search                 # 검색
└── infra/
    ├── redis                  # Redis 캐시
    ├── storage                # S3, CloudFront
    ├── gpt                    # OpenAI 연동
    └── tmdb                   # TMDB API 연동
```

### 2.2 의존성 규칙

- `apps:*` → `modules:*`, `infra:*` 의존 가능
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

1. UserKeyword 테이블에서 해당 사용자의 키워드 목록 조회
2. 키워드별 비율(percentage) 포함하여 반환

**[응답]**

- keywords: 키워드 목록
    - name (String): 키워드명
    - percentage (Integer): 비율 (0~100)
    - color (String): 표시 색상

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

#### 3.3.2 탐색 - 컬렉션 목록 조회

`GET /collections`

**[입력]**

- cursor (Long, optional): 페이지네이션 커서
- size (Integer, default: 10): 조회 개수

**[처리 로직]**

1. 공개 컬렉션 목록 조회
2. 추천 전략에 따라 정렬/필터링
3. Cursor 기반 페이지네이션 적용

**[추천 전략]**

- MVP: 랜덤/샘플링
- 후속: 개인화 추천 (취향 키워드 기반)

**[응답]**

- items: 컬렉션 목록
- nextCursor (Long): 다음 페이지 커서
- hasNext (Boolean): 다음 페이지 존재 여부

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
- author: 작성자 정보
- contents: 콘텐츠 목록
- isBookmarked (Boolean): 북마크 여부

---

#### 3.3.4 최근 본 컬렉션 목록 조회

`GET /collections/recent`

**[입력]**

- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. RecentViewedCollection 테이블에서 최근 조회 기록 조회
2. last_viewed_at 기준 내림차순 정렬

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

1. 해당 컬렉션의 북마크 존재 여부 확인
2. **북마크 존재**: 삭제 (북마크 해제)
3. **북마크 미존재**: 생성 (북마크 추가)

**[응답]**

- isBookmarked (Boolean): 토글 후 북마크 상태

---

#### 3.4.3 컬렉션 북마크 사용자 목록 조회

`GET /bookmarks/{collectionId}`

**[입력]**

- collectionId (Long, Path): 컬렉션 ID

**[처리 로직]**

1. 해당 컬렉션을 북마크한 사용자 목록 조회
2. 북마크 수 집계

**[응답]**

- users: 북마크한 사용자 목록
- count (Integer): 총 북마크 수

---

### 3.5 콘텐츠 (Content)

---

#### 3.5.1 콘텐츠별 OTT 목록 조회

`GET /contents/ott/{contentId}`

**[입력]**

- contentId (Long, Path): 콘텐츠 ID
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 해당 콘텐츠를 시청 가능한 OTT 목록 조회
2. 사용자가 구독 중인 OTT 목록 조회
3. 각 OTT별 구독 여부 표시

**[응답]**

- otts: OTT 목록
    - name (String): OTT 이름
    - logoUrl (String): 로고 URL
    - isSubscribed (Boolean): 사용자 구독 여부

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

**[응답]**

- data: 북마크한 콘텐츠 목록
- meta.type: CURSOR
- meta.returned: 현재 응답의 콘텐츠 수
- meta.nextCursor: 다음 페이지 조회용 cursor

---

#### 3.5.3 콘텐츠 검색 (TMDB)

`GET /contents/search`

**[입력]**

- keyword (String): 검색어
- cursor (Integer, default: 1): 페이지 번호
- size (Integer, default: 20): 조회 개수

**[처리 로직]**

1. TMDB API에 검색 요청
2. 검색 결과를 내부 DTO로 변환
3. 페이지네이션 정보 포함하여 반환

**[응답]**

- items: 검색 결과 목록
- nextCursor (Integer): 다음 페이지 번호
- hasNext (Boolean): 다음 페이지 존재 여부

---

### 3.6 검색 (Search)

---

#### 3.6.1 콘텐츠 검색 (DB)

`GET /search/contents`

**[입력]**

- keyword (String): 검색어

**[처리 로직]**

1. DB에 저장된 콘텐츠 중 제목으로 검색
2. LIKE 검색 적용

**[응답]**

- contents: 검색 결과 목록

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

- keyword (String): 검색어
- cursor (Long, optional): 페이지네이션 커서
- size (Integer, default: 20): 조회 개수
- Authorization Header: Bearer {accessToken}

**[처리 로직]**

1. 사용자가 북마크한 콘텐츠 중 검색
2. 콘텐츠 제목으로 LIKE 검색
3. Cursor 기반 페이지네이션 적용

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

### 4.2 주요 제약조건

| 테이블 | UNIQUE 제약 |
|--------|-------------|
| user_identities | (provider, provider_user_id) |
| contents | (tmdb_id) |
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
| GET | /collections/recent | 최근 본 목록 | O |

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
| GET | /contents/search | TMDB 검색 | X |

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
| GET | /home/popular-collections | 인기 컬렉션 | X |

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
