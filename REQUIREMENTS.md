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
2. 최대 6개를 반환하고 응답 순위를 1~6으로 정규화
3. 1~3위는 코어 키워드, 4~6위는 서브 키워드로 사용
4. 재계산 결과는 기존 키워드 전체를 교체하며 GPT가 동점 순위를 반환해도 고유 순위를 저장
5. 1~3위는 기존 레벨 색상을 우선 사용하되 중복 시 `PINK → GREEN → ORANGE → YELLOW → BLUE` 순으로 미사용 색상을 배정
6. 4~6위는 기존 레벨 색상을 유지

**[응답]**

- keywords: 키워드 목록
    - name (String): 키워드명
    - percentage (Integer): 비율 (0~100)
    - color (String): 표시 색상
    - rank (Integer): 정규화된 순위 (1~6)

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
2. 사용자별 세션 시작 커서를 저장하고 작품 ID 오름차순으로 30개 구성
3. 각 작품에는 TMDB 줄거리 대신 대표 컬렉션 작성자의 선정 이유(`CollectionContent.reason`)를 반환
4. 현재 세션이 끝난 경우 `POST /exploration/next`로 다음 30개 세션으로 이동

**[세션 규칙]**

- 완전한 30개가 준비된 경우에만 세션을 반환
- 신규 작품은 더 큰 ID로 뒤에 추가되므로 진행 중인 세션의 기본 윈도우는 유지
- 서버는 현재 30개 세션과 완료 여부를 저장하지만 세션 내부의 마지막 열람 인덱스는 저장하지 않음
- 앱 이탈 후 정확한 카드 위치 복원은 진행 인덱스 갱신 API와 클라이언트 호출 정책을 추가해야 함

**[응답]**

- items: 작품 목록 (30개 또는 빈 배열)
- state: IN_PROGRESS, END, EMPTY
- hasNext (Boolean): 다음 30개 세션 존재 여부

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

**[응답]**

- data: 북마크한 콘텐츠 목록
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

- keyword (String, optional): 한글 또는 영문 검색어
- genre (List, optional): 장르 목록. 여러 값은 OR
- mediaType (MOVIE/TV, optional): 미디어 타입
- cursor (String, optional): 이전 응답의 opaque cursor
- size (Integer, default: 20): 조회 개수

**[처리 로직]**

1. 검색어의 공백·특수문자·이모지와 대소문자를 정규화
2. `title_ko`, `title_en`, 정규화 제목을 합친 `search_title` ngram FULLTEXT 검색
3. 검색어가 있으면 정규화 완전 일치, FULLTEXT 관련도, 콘텐츠 ID 내림차순으로 정렬하며 인기순은 적용하지 않음
4. 검색어가 없으면 북마크 수, 콘텐츠 ID 내림차순으로 정렬
5. 장르 목록 내부는 OR, 검색어·장르 그룹·미디어 타입 사이는 AND
6. 정렬 모드·완전 일치 등급·관련도·ID 또는 북마크 수·ID를 담은 버전형 cursor로 페이지 이동

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

- keyword (String, optional): 검색어

**[처리 로직]**

1. 검색어가 있으면 DB에 저장된 콘텐츠 중 제목 부분 일치 검색
2. 키워드 검색 결과는 개수 제한 없이 반환
3. 검색어가 없으면 북마크 수 내림차순, 콘텐츠 ID 내림차순으로 30개 반환
4. 기본 목록 정렬은 `idx_content_popular(bookmark_count DESC, id DESC)` 인덱스 순서와 일치

**[응답]**

- contents: 검색 결과 목록

**[현재 제한 및 클라이언트 책임]**

- 콘텐츠 원산지/제작 국가 필드가 없어 국내 작품 비율을 서버에서 보장할 수 없음. 고정 비율이 필요하면 국가 메타데이터 또는 온보딩 큐레이션 테이블이 필요함
- 이 호환 API는 정확 일치/유사도 점수 정렬과 오타 보정을 제공하지 않음. 관련도 정렬은 FULLTEXT 점수와 그 점수를 포함하는 커서 계약을 별도로 설계해야 함
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

#### 3.7.2 인기 컬렉션 조회

`GET /home/popular-collections`

- 컬렉션별 `imageList`에는 작품 공식 포스터만 최대 2개 반환
- 각 항목에 총 `bookmarkCount`를 포함
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
| GET | /home/popular-collections | 인기 컬렉션 | X |

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
