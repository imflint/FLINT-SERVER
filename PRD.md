# PRD

## 0) 변경/추가된 결정 사항

### 0.1 홈(Home)

* MVP에서는 홈 응답 조립을 `apps:api`에서 수행한다.
* `HomeQueryFacade`로 캡슐화하여, 추후 `modules:home`로 구현 이동이 가능하도록 한다.
* 분리 트리거 충족 시(슬롯 증가/캐시·집계 도입/재사용 증가 등) `modules:home`로 이동한다.

### 0.2 탐색(Discovery)

* MVP 탐색 탭은 **공개 컬렉션 “랜덤(또는 샘플링)” 노출**이 목적이다.
* 그러나 추후 개인화 추천을 넣기 위해, MVP부터 다음을 고정한다.

    1. **추천 계약(Recommendation Contract)**: “랜덤도 추천의 한 전략”으로 취급
    2. **상호작용 로그 최소 수집**: 특히 **impression(노출)**을 MVP부터 적재
    3. **추천 파이프라인 분해**: 후보 생성 → 랭킹 → 필터 → 섞기(mix) 구조로 확장 가능하게 설계
* MVP에서는 `apps:api`에서 조립하되, 추천이 복잡해지면 `modules:discovery`(또는 `modules:recommendation`)로 구현을 이동한다.

---

# 1) 시스템/모듈 구조(업데이트 반영)

## 1.1 MVP 기준

* `apps:api`
* (선택) `apps:worker`
* `modules:shared`
* `modules:user`
* `modules:auth`
* `modules:content`
* `modules:collection`
* `modules:bookmark`
* `modules:taste`
* `modules:ott`
* `modules:search`
* `infra:redis` (캐싱 인프라)
* `infra:s3` (파일 저장소 인프라)
* **`modules:home` / `modules:discovery`는 MVP에서는 api에서 조합하여 구현**
    * 대신 `apps:api`의 `HomeQueryFacade`, `DiscoveryQueryFacade`로 캡슐화
    * 추후 이동 가능한 포트(인터페이스)만 유지

## 1.2 모듈 의존성 규칙(고정)

* `apps:*` → `modules:*`, `infra:*` 의존
* `modules:*` → `modules:shared`만 의존 (다른 도메인 모듈 의존 금지)
* `infra:*` → 외부 라이브러리만 의존 (도메인 모듈 의존 금지)
* 엔티티는 단일 모듈 소유, 다른 모듈은 ID 참조
* 경계는 ArchUnit으로 강제

---

# 2) MVP 기간 PRD(기능 목록 유지 + 탐색/로그 추가)

## 2.1 MVP API 목록(기존 + 탐색 탭 명시)

사용자가 제시한 MVP API 목록은 유지하되, **탐색 탭**을 다음 API로 명확히 정의한다.

* 탐색

    * 컬렉션 리스트 조회(탐색 탭용)  ← **랜덤/샘플링 전략**
    * 컬렉션 상세조회

> 기존 “탐색: 컬렉션 리스트 조회”를 “탐색 탭의 랜덤 피드”로 명시한 것뿐이며, API 갯수는 늘리지 않습니다.

---

## 2.2 홈(Home) MVP 구현 스펙(유지)

* `GET /v1/home`

    * recommended collections
    * recent bookmarked contents
    * bookmarked collections
* 조립: `apps:api`의 `HomeQueryFacade`
* 추천 규칙: 공개 컬렉션 최신+인기(북마크 수) 혼합
* 캐시/집계는 MVP에서 필수 아님(후속 트리거 시 `modules:home`로 이동)

---

## 2.3 탐색(Discovery) MVP 구현 스펙(추가/고정)

### 2.3.1 API 권장 정의

* `GET /v1/discovery/collections?cursor&size`

    * MVP: 공개 컬렉션 랜덤(또는 샘플링) N개
    * 필터(권장 최소):

        * `is_public=true`
        * 내 컬렉션 제외(선택)
        * 이미 북마크한 컬렉션 제외(선택)
* `GET /v1/collections/{id}` (상세조회)

    * 최근 본 기록 upsert

### 2.3.2 조립 위치/구조(MVP)

* `apps:api`

    * `DiscoveryController` → `DiscoveryQueryFacade`
* Facade 호출:

    * `collection` 모듈의 공개 컬렉션 조회
    * `bookmark` 모듈의 “북마크/최근 조회” 필터 데이터(선택)

### 2.3.3 “추천 계약” 고정(확장 준비)

MVP부터 아래 형태의 계약을 코드 레벨에서 유지한다.

* `CollectionRecommendationPort.recommend(userId, size, cursor, context)`

MVP 구현체:

* `RandomCollectionRecommendation` (랜덤/샘플링)

후속 구현체:

* `PersonalizedCollectionRecommendation`
* `TrendingCollectionRecommendation`
* (팔로우 도입 시) `FollowingBasedRecommendation`

> 이렇게 하면 “탐색 탭의 API”는 그대로 두고 내부 전략만 교체할 수 있습니다.

---

## 2.4 MVP에서 반드시 쌓아야 할 추천 신호(추가 요구사항)

개인화 추천 전환을 위해 MVP에서 최소 수집:

1. **Impression(노출)**: 탐색 탭에서 어떤 컬렉션을 사용자에게 보여줬는지
2. **Click(상세 진입)**: 탐색 탭에서 상세로 들어갔는지
3. **Bookmark(저장)**: 기존 bookmark 테이블로 충분
4. **View(최근 조회)**: 기존 recent_viewed_collections로 충분

### MVP 구현 원칙

* Impression은 “사용자가 보지 않았는데 서버가 기록”하는 왜곡을 막기 위해 선택지가 있습니다.

    * (권장) 서버가 결과를 내려줄 때 “응답에 포함된 컬렉션 목록”을 impression으로 기록(서버 중심)
    * 또는 클라이언트가 실제 렌더 시점에 impression API를 따로 호출(정확하지만 개발 비용 상승)
* MVP에서는 서버 기록 방식이 현실적입니다(오차는 있으나 데이터가 쌓입니다).

---

## 2.5 추천 파이프라인 구조(후속 확장 규격)

MVP에서는 랜덤이지만, PRD 상 추천 구조를 고정합니다.

* Candidate(후보): 랜덤/인기/최신/취향 유사 후보 생성
* Rank(랭킹): 간단 점수식(키워드 유사도 + 최신성 + 인기)
* Filter(필터): 비공개/차단/이미 본 것 제외 등
* Mix(섞기): 탐험(exploration) 비율 유지(일부 랜덤 혼합)

---

# 3) 이후 스프린트 PRD(탐색 개인화 포함 업데이트)

## Sprint 1: 팔로우/맞팔로우 + 홈/탐색 슬롯 확장

* `modules:social`(Follow/Block)
* 탐색/홈에 “팔로잉 기반 후보” 추가
* 이 시점부터 추천 전략 다변화 가능성이 높아져 `modules:discovery` 분리 트리거 발생 가능

## Sprint 2: 질문(7일) + 결과 24시간 노출 + 2뷰

* `modules:question`
* 질문 응답 데이터는 추천 신호로도 활용(고품질)

## Sprint 3: 취향 키워드 3계층 고도화

* taste 고도화(Keyword/ContentKeyword 운영 및 파이프라인)
* 개인화 추천 Stage 1(키워드 기반 점수식) 본격 적용 가능

## Sprint 4: 검색(OpenSearch) + 추천 후보 생성 강화

* OpenSearch로 후보 생성(쿼리/유사도 기반)
* outbox+worker로 인덱싱 동기화

## Sprint 5+: 그래프/벡터/RAG(선택)

* Neo4j(관계 기반)
* 벡터(유사 컬렉션)
* RAG(추천 이유 설명)

---

# 4) “modules:discovery(추천 전용 모듈)” 분리 기준(명시적으로 PRD에 고정)

아래 중 1~2개만 충족해도 분리 권장:

1. 추천 전략이 2~3개 이상으로 증가(랜덤/인기/개인화/팔로우 등)
2. Redis 캐시/집계 테이블/배치(워커)가 필요
3. A/B 테스트, 모델 버전 관리가 필요
4. 추천이 탐색 탭뿐 아니라 홈/마이페이지/질문 등 다수 화면에 재사용
5. impression/click 로그를 이용한 “피드백 루프”가 본격화

분리의 의미는 “마이크로서비스”가 아니라, 모놀리식 내부에서:

* `modules:discovery`가 추천 전략/읽기 모델/집계/캐시를 소유하도록 이동하는 것입니다.

---

# 5) 데이터 모델 업데이트(탐색 로그 추가)

## 5.1 추천 확장을 위한 최소 테이블(권장)

MVP 개인화 확장성을 위해 아래 중 1개는 추가를 권장합니다.

### 옵션 A: 전용 테이블(명확, 쿼리 쉬움)

* `discovery_impressions`

    * `id(TSID)`, `user_id`, `collection_id`, `surfaced_at`, `surface`, `session_id(optional)`

### 옵션 B: 확장형 이벤트 로그(범용)

* `interaction_events`

    * `id(TSID)`, `user_id`, `entity_type`, `entity_id`, `event_type(impression/click/bookmark)`, `occurred_at`, `metadata(json)`

MVP 개발 속도를 고려하면 **옵션 B가 확장성이 더 좋고, 스키마 변경이 적습니다.**

---

# 6) 구현 책임 위치(최종 정리)

* MVP 홈/추천(탐색)은 **`apps:api`에서 조립** 

    * `HomeQueryFacade`, `DiscoveryQueryFacade`로 캡슐화
* 홈/추천(탐색)이 커지면

    * `modules:home`, `modules:discovery`로 구현 이동
    * 포트(인터페이스) 유지로 API 레이어 변경 최소화

좋습니다. 아래는 **직전 PRD(홈은 apps:api 조립, 탐색은 MVP 랜덤→추후 개인화 추천)**에 그대로 덧붙일 수 있도록, **“도메인(모듈)별 엔티티/테이블 소유권”**을 **MVP 기준 + 확장 시점**까지 함께 정리한 섹션입니다.

---

## 7) 도메인(모듈)별 소유 엔티티/테이블 (MVP 기준 + 확장)

> 원칙: **테이블/엔티티는 한 도메인이 소유(SoT)**하고, 다른 도메인은 **ID 참조**만 합니다.
> 물리 FK는 초기엔 “선택”이지만, **UNIQUE는 반드시 설계에 포함**합니다.
> (TSID 사용: 모든 PK는 BIGINT, API 응답은 String)

---

### 7.2 `modules:user` (유저 프로필 SoT)

#### 소유 엔티티/테이블

* `User` → `users`

    * `user_id`(TSID, PK)
    * `nickname`
    * `profile_image`
    * `user_role`
    * `created_at`, `updated_at`

---

### 7.3 `modules:auth` (인증/계정 연결 SoT)

#### 소유 엔티티/테이블

* `UserIdentity` → `user_identities`

    * `id`(TSID, PK)
    * `user_id`
    * `provider` (MVP: `KAKAO` / 후속: `APPLE`, `GOOGLE`)
    * `provider_user_id`
    * `created_at`
    * **UNIQUE**: `(provider, provider_user_id)`

#### 인증 전략

* **소셜 로그인**: 클라이언트에서 Provider SDK로 인증 → 백엔드에서 검증
    * MVP: Kakao
    * 후속: Apple, Google
* **JWT 라이브러리**: `jjwt` (io.jsonwebtoken)
* **Access Token**: 짧은 만료 시간 (15분~1시간)
* **Refresh Token**: Redis에 저장 (만료 시 자동 삭제)
    * Key: `refresh:{userId}:{tokenId}`
    * Value: Refresh Token
    * TTL: 14일

#### 소셜 로그인 플로우 (Web vs Mobile)

**Web 플로우 (Authorization Code)**
```
1. 사용자 → 카카오 로그인 페이지
2. 카카오 → redirect_uri로 authorization code 전달
3. 클라이언트 → 서버에 code 전송
4. 서버 → 카카오에 code로 access_token 교환
5. 서버 → 카카오에 access_token으로 사용자 정보 조회
6. 서버 → 클라이언트에 인증 결과 반환
```

**Mobile 플로우 (Access Token)**
```
1. 사용자 → 카카오 SDK로 로그인
2. 카카오 SDK → 앱에 access_token 직접 반환
3. 클라이언트 → 서버에 access_token 전송
4. 서버 → 카카오에 access_token으로 사용자 정보 조회
5. 서버 → 클라이언트에 인증 결과 반환
```

**API 요청 형식**
```json
// Web (code 사용)
{ "provider": "KAKAO", "code": "authorization_code" }

// Mobile (accessToken 사용)
{ "provider": "KAKAO", "accessToken": "access_token_from_sdk" }
```

#### 회원가입 플로우

1. `POST /auth/social/verify` - 소셜 로그인 검증
    * 입력: `code` (Web) 또는 `accessToken` (Mobile)
    * 기존 회원: Access/Refresh Token 반환
    * 신규 회원: `tempToken` 반환 (회원가입용 임시 토큰)
2. `GET /users/nickname/check` - 닉네임 중복 체크
3. `POST /auth/signup` - 회원가입 완료
    * User, UserIdentity, ContentBookmark(좋아하는 작품), UserOtt(구독 OTT) 생성
    * Access/Refresh Token 반환

---

### 7.4 `modules:content` (작품 메타 SoT)

#### 소유 엔티티/테이블

* `Content` → `contents`

    * `content_id`(TSID, PK)
    * `tmdb_id` (UNIQUE)
    * `content_type` (`MOVIE`, `TV`, `VARIETY`)
    * `title`, `description`, `poster`
    * `release_year`
    * (MVP 임시) `raw_genre` (VARCHAR/TEXT)
    * `created_at`, `updated_at`
    * **UNIQUE**: `(tmdb_id)`
* (후속) 다중 외부 소스 확장 시

    * `ContentExternalRef` → `content_external_refs`

        * `(source, external_id)` UNIQUE

> 비고: MVP에서 `genre`를 단일 varchar로 저장하더라도, 후속에서 `taste.content_keywords`로 정규화/확장 가능하도록 content는 “원천 메타만” 소유합니다.

---

### 7.5 `modules:collection` (컬렉션 SoT)

#### 소유 엔티티/테이블

* `Collection` → `collections`

    * `collection_id`(TSID, PK)
    * `author_user_id`
    * `title`, `description`, `collection_image`
    * `is_public`
    * (후속) `status` (`DRAFT`, `PUBLISHED`)
    * `created_at`, `updated_at`

* `CollectionContents` → `collection_contents`

    * `id`(TSID, PK)
    * `collection_id`
    * `content_id`
    * `position` (1~10)
    * `is_spoiler`
    * `selection_reason` (TEXT)
    * `created_at`
    * **UNIQUE**: `(collection_id, position)`, `(collection_id, content_id)`

* `RecentViewedCollection` → `recent_viewed_collections`

    * `id`(TSID, PK)
    * `user_id`, `collection_id`
    * `last_viewed_at`
    * **UNIQUE**: `(user_id, collection_id)`

> 비고: `RecentViewedCollection`은 Collection의 라이프사이클에 종속되므로 collection 모듈에서 소유합니다. Collection 삭제 시 함께 삭제됩니다.

---

### 7.6 `modules:bookmark` (유저 저장 SoT)

#### 소유 엔티티/테이블

* `ContentBookmark` → `content_bookmarks`

    * `content_bookmark_id`(TSID, PK)
    * `user_id`, `content_id`
    * `created_at`
    * **UNIQUE**: `(user_id, content_id)`

* `CollectionBookmark` → `collection_bookmarks`

    * `collection_bookmark_id`(TSID, PK)
    * `user_id`, `collection_id`
    * `created_at`
    * **UNIQUE**: `(user_id, collection_id)`

---

### 7.7 `modules:taste` (취향 키워드/프로파일 SoT)

> Genre(장르)와 Keyword(취향 키워드)는 별도 개념으로 분리합니다.
> - **Genre**: 작품의 객관적 분류 (로맨스, 액션, 스릴러 등) - content 모듈에서 관리
> - **Keyword**: 사용자 취향/감상 키워드 (힐링, 반전, 성장, 감동 등) - taste 모듈에서 관리

#### 소유 엔티티/테이블

* `Keyword` → `keywords`

    * `keyword_id`(TSID, PK)
    * `name` (힐링, 반전, 성장, 감동, 복수 등)
    * **UNIQUE**: `(name)`

* `ContentKeyword` → `content_keywords`

    * `id`(TSID, PK)
    * `content_id`, `keyword_id`
    * `weight`, `confidence`
    * **UNIQUE**: `(content_id, keyword_id)`

* `UserKeyword` → `user_keywords`

    * `id`(TSID, PK)
    * `user_id`, `keyword_id`
    * `percentage` (0~100, 사용자의 해당 키워드 비율)
    * `updated_at`
    * **UNIQUE**: `(user_id, keyword_id)`

---

### 7.8 `modules:ott` (OTT 플랫폼/구독 SoT)

#### 소유 엔티티/테이블

* `OttProvider` → `ott_providers`

    * `id`(TSID, PK)
    * `name` (Netflix, Disney+, Wavve 등)
    * `logo_url`
    * `created_at`
    * **UNIQUE**: `(name)`

* `ContentOtt` → `content_otts`

    * `id`(TSID, PK)
    * `content_id`, `ott_provider_id`
    * `created_at`
    * **UNIQUE**: `(content_id, ott_provider_id)`

* `UserOtt` → `user_otts`

    * `id`(TSID, PK)
    * `user_id`, `ott_provider_id`
    * `created_at`
    * **UNIQUE**: `(user_id, ott_provider_id)`

> OttProvider는 마스터 데이터, ContentOtt는 작품별 시청 가능 OTT, UserOtt는 유저별 구독 OTT

---

### 7.10 `modules:search` (MVP: DB 검색, 후속: OpenSearch)

#### 소유 엔티티/테이블

* MVP: 없음(컬렉션/콘텐츠 테이블을 DB로 검색)
* (후속) `SearchIndexCursor` → `search_index_cursors` (선택)

    * 인덱싱 상태 추적용

---

### 7.11 `modules:home` (MVP는 apps:api 조립, 후속 분리 가능)

#### 소유 엔티티/테이블

* MVP: 없음(조립은 `HomeQueryFacade`가 수행)
* (후속 트리거 충족 시) `CollectionStats` → `collection_stats` (권장)

    * `collection_id`(PK or UNIQUE)
    * `bookmark_count`, `updated_at`, `last_bookmarked_at`
    * 업데이트는 Outbox/Worker 기반으로 전환 가능

---

### 7.12 `modules:discovery` (탐색/추천 — MVP는 apps:api 조립, 후속 분리 가능)

#### 소유 엔티티/테이블

* MVP: 없음(랜덤 추천은 `DiscoveryQueryFacade`에서 조립)
* (후속 트리거 충족 시) 추천 전용 테이블은 “필수는 아님”

    * 추천 품질/성능 때문에 필요해질 경우에만 도입:

        * `discovery_feed_state` (유저별 마지막 노출 커서/탐험 비율 등)
        * `recommendation_experiments` (A/B 실험/버전 관리)
* 추천 학습/피드백 신호는 **`shared.interaction_events`를 소비**하는 것을 기본으로 한다.

---

## 8) MVP에서 “실제로 생성해야 하는 테이블” 체크리스트

MVP 기간(현재 API 범위)에서 최소로 필요한 테이블은 아래입니다.

* `users`
* `user_identities` (+ 선택: `refresh_tokens/sessions`)
* `contents`
* `collections`, `collection_contents`
* `content_bookmarks`, `collection_bookmarks`
* `recent_viewed_collections`
* `ott_providers`, `content_otts`, `user_otts`
* (추천) `interaction_events`  ← 탐색(랜덤→개인화) 전환의 핵심 기반

`taste` 관련 3개(`keywords/content_keywords/user_keywords`)는 **MVP에서 조회가 단순 집계**면 "선택"이지만, 후속 확장이 확실하므로 **골격만 먼저 만들어두는 것**을 권장합니다.