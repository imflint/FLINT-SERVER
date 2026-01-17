# Flint Mock Data

## 테이블 목록

| 엔티티 | 테이블명 | 비고 |
|--------|----------|------|
| User | `user` | soft delete |
| UserIdentity | `user_identity` | |
| Genre | `genre` | |
| Keyword | `keywords` | level, image 포함 |
| Content | `content` | |
| ContentGenre | `content_genre` | |
| ContentKeyword | `content_keywords` | 커스텀 테이블명 |
| UserKeyword | `user_keywords` | 커스텀 테이블명 |
| OttProvider | `ott_provider` | |
| OttContent | `ott_content` | |
| OttUser | `ott_user` | |
| Collection | `collection` | soft delete |
| CollectionContent | `collection_content` | |
| ContentBookmark | `content_bookmark` | |
| CollectionBookmark | `collection_bookmark` | |
| RecentViewedCollection | `recent_viewed_collection` | |

---

## Mock Data INSERT

```sql
-- =============================================
-- FLINT Mock Data
-- 실행 순서대로 정렬됨 (FK 의존성 고려)
-- =============================================

-- 1. Users (사용자)
INSERT INTO user (id, nickname, profile_image, user_role, status, created_at, updated_at) VALUES
(1, '플리너원', 'https://example.com/profile1.jpg', 'FLINER', 'ACTIVE', NOW(), NOW()),
(2, '일반유저', 'https://example.com/profile2.jpg', 'FLING', 'ACTIVE', NOW(), NOW()),
(3, '콘텐츠매니아', NULL, 'FLING', 'ACTIVE', NOW(), NOW());

-- 2. User Identity (소셜 로그인 정보)
INSERT INTO user_identity (id, user_id, provider, provider_user_id, created_at, updated_at) VALUES
(1, 1, 'KAKAO', 'kakao_12345', NOW(), NOW()),
(2, 2, 'KAKAO', 'kakao_67890', NOW(), NOW()),
(3, 3, 'APPLE', 'apple_abcde', NOW(), NOW());

-- 3. Genres (장르) - Content 모듈용
INSERT INTO genre (id, name) VALUES
(1, '로맨스'),
(2, '액션'),
(3, '스릴러'),
(4, '코미디'),
(5, '드라마'),
(6, 'SF'),
(7, '판타지'),
(8, '공포');

-- 4. Keywords (취향 키워드) - 레벨별 분류
INSERT INTO keywords (id, name, level, image) VALUES
-- L1 장르 (PINK) - 분홍 반원/원형 블록
(1, '드라마', 'L1', 'pink_half_circle'),
(2, '코미디', 'L1', 'pink_half_circle'),
(3, '모험', 'L1', 'pink_half_circle'),
(4, '음악', 'L1', 'pink_half_circle'),
(5, '역사', 'L1', 'pink_half_circle'),
-- L1 장르 (PINK) - 분홍 삼각형 패턴
(6, '액션', 'L1', 'pink_triangle'),
(7, '범죄', 'L1', 'pink_triangle'),
(8, '미스터리', 'L1', 'pink_triangle'),
(9, '스릴러', 'L1', 'pink_triangle'),
(10, '전쟁', 'L1', 'pink_triangle'),
(11, '서부', 'L1', 'pink_triangle'),
-- L1 장르 (PINK) - 분홍 잎/꽃잎 모양
(12, '애니메이션', 'L1', 'pink_leaf'),
(13, '판타지', 'L1', 'pink_leaf'),
(14, 'SF', 'L1', 'pink_leaf'),
(15, '로맨스', 'L1', 'pink_leaf'),

-- L2 분위기/감정 (GREEN) - 초록 1/4원 형태
(16, '슬픈', 'L2', 'green_quarter_circle'),
(17, '불안한', 'L2', 'green_quarter_circle'),
-- L2 분위기/감정 (GREEN) - 아래로 향한 V/화살표 형태
(18, '어두운', 'L2', 'green_arrow_down'),
(19, '긴장되는', 'L2', 'green_arrow_down'),
(20, '잔혹한', 'L2', 'green_arrow_down'),
-- L2 분위기/감정 (GREEN) - 클로버/꽃잎 4개 형태
(21, '잔잔한', 'L2', 'green_clover'),
(22, '설레는', 'L2', 'green_clover'),
(23, '몽환적인', 'L2', 'green_clover'),
-- L2 분위기/감정 (GREEN) - 초록 별/톱니 형태
(24, '유쾌한', 'L2', 'green_star'),

-- L3 서사/테마 (ORANGE) - 물방울/둥근 D 형태
(25, '노년의 삶', 'L3', 'orange_droplet'),
(26, '실화기반', 'L3', 'orange_droplet'),
(27, '사회문제', 'L3', 'orange_droplet'),
(28, '정체성', 'L3', 'orange_droplet'),
(29, '권력', 'L3', 'orange_droplet'),
-- L3 서사/테마 (ORANGE) - 팩맨처럼 파인 원형 형태
(30, '오피스', 'L3', 'orange_pacman'),
(31, '복수', 'L3', 'orange_pacman'),
(32, '추리', 'L3', 'orange_pacman'),
(33, '반전 중심', 'L3', 'orange_pacman'),
-- L3 서사/테마 (ORANGE) - 반원 3단 적층 형태
(34, '가족중심', 'L3', 'orange_semicircle_stack'),
(35, '성장', 'L3', 'orange_semicircle_stack'),
(36, '종교', 'L3', 'orange_semicircle_stack'),
-- L3 서사/테마 (ORANGE) - 4개 꽃잎/블록 형태
(37, '브로맨스', 'L3', 'orange_four_petals'),
(38, '우정', 'L3', 'orange_four_petals'),
(39, '청춘', 'L3', 'orange_four_petals'),
(40, '생존', 'L3', 'orange_four_petals'),

-- L4 배경/문화권 (YELLOW) - 노란 반원 2개가 마주보는 형태
(41, '사극', 'L4', 'yellow_facing_semicircles'),
(42, '디스토피아', 'L4', 'yellow_facing_semicircles'),
(43, '근미래', 'L4', 'yellow_facing_semicircles'),
(44, '근대', 'L4', 'yellow_facing_semicircles'),
-- L4 배경/문화권 (YELLOW) - 노란 원 4개 묶음 형태
(45, '한국', 'L4', 'yellow_four_circles'),
(46, '북미', 'L4', 'yellow_four_circles'),
(47, '유럽', 'L4', 'yellow_four_circles'),
(48, '인도', 'L4', 'yellow_four_circles'),
(49, '일본', 'L4', 'yellow_four_circles'),
(50, '영국', 'L4', 'yellow_four_circles'),

-- L5 포맷 (BLUE) - 파란 아치/∩ 형태
(51, '영화', 'L5', 'blue_arch'),
(52, '시리즈', 'L5', 'blue_arch'),
(53, '다큐멘터리', 'L5', 'blue_arch'),
(54, '예능', 'L5', 'blue_arch');

-- 5. Contents (콘텐츠)
INSERT INTO content (id, tmdb_id, title, year, author, description, poster, bookmark_count, created_at, updated_at) VALUES
(1, 100001, '눈물의 여왕', 2024, '박지은', '재벌가 상속녀와 평범한 남자의 사랑 이야기', 'https://image.tmdb.org/poster1.jpg', 150, NOW(), NOW()),
(2, 100002, '더 글로리', 2023, '김은숙', '학교폭력 피해자의 복수극', 'https://image.tmdb.org/poster2.jpg', 320, NOW(), NOW()),
(3, 100003, '이상한 변호사 우영우', 2022, '문지원', '자폐 스펙트럼을 가진 변호사의 성장기', 'https://image.tmdb.org/poster3.jpg', 280, NOW(), NOW()),
(4, 100004, '무빙', 2023, '강풀', '초능력을 가진 부모와 자녀들의 이야기', 'https://image.tmdb.org/poster4.jpg', 200, NOW(), NOW()),
(5, 100005, '스위트홈', 2020, '김칸비', '괴물로 변하는 인간들과의 생존기', 'https://image.tmdb.org/poster5.jpg', 180, NOW(), NOW());

-- 6. Content Genres (콘텐츠-장르 연결)
INSERT INTO content_genre (id, content_id, genre_id) VALUES
(1, 1, 1),  -- 눈물의여왕 - 로맨스
(2, 1, 5),  -- 눈물의여왕 - 드라마
(3, 2, 3),  -- 더글로리 - 스릴러
(4, 2, 5),  -- 더글로리 - 드라마
(5, 3, 5),  -- 우영우 - 드라마
(6, 3, 4),  -- 우영우 - 코미디
(7, 4, 2),  -- 무빙 - 액션
(8, 4, 6),  -- 무빙 - SF
(9, 5, 8),  -- 스위트홈 - 공포
(10, 5, 3); -- 스위트홈 - 스릴러

-- 7. Content Keywords (콘텐츠-키워드 연결)
INSERT INTO content_keywords (id, content_id, keyword_id, confidence) VALUES
(1, 1, 15, 0.95),  -- 눈물의여왕 - 로맨스
(2, 1, 16, 0.90),  -- 눈물의여왕 - 슬픈
(3, 1, 52, 0.98),  -- 눈물의여왕 - 시리즈
(4, 2, 31, 0.98),  -- 더글로리 - 복수
(5, 2, 19, 0.95),  -- 더글로리 - 긴장되는
(6, 2, 33, 0.92),  -- 더글로리 - 반전 중심
(7, 3, 35, 0.95),  -- 우영우 - 성장
(8, 3, 24, 0.90),  -- 우영우 - 유쾌한
(9, 4, 6, 0.93),   -- 무빙 - 액션
(10, 4, 34, 0.90), -- 무빙 - 가족중심
(11, 5, 40, 0.96), -- 스위트홈 - 생존
(12, 5, 19, 0.91); -- 스위트홈 - 긴장되는

-- 8. User Keywords (사용자 취향 키워드)
INSERT INTO user_keywords (id, user_id, keyword_id, percentage, created_at, updated_at) VALUES
(1, 1, 21, 85, NOW(), NOW()),  -- 플리너원 - 잔잔한 85%
(2, 1, 15, 72, NOW(), NOW()),  -- 플리너원 - 로맨스 72%
(3, 1, 35, 65, NOW(), NOW()),  -- 플리너원 - 성장 65%
(4, 2, 33, 90, NOW(), NOW()),  -- 일반유저 - 반전 중심 90%
(5, 2, 19, 78, NOW(), NOW()),  -- 일반유저 - 긴장되는 78%
(6, 3, 6, 88, NOW(), NOW()),   -- 콘텐츠매니아 - 액션 88%
(7, 3, 40, 70, NOW(), NOW());  -- 콘텐츠매니아 - 생존 70%

-- 9. OTT Providers (OTT 플랫폼)
INSERT INTO ott_provider (id, name, logo_url, url) VALUES
(1, '넷플릭스', 'https://example.com/netflix.png', 'https://netflix.com'),
(2, '왓챠', 'https://example.com/watcha.png', 'https://watcha.com'),
(3, '웨이브', 'https://example.com/wavve.png', 'https://wavve.com'),
(4, '티빙', 'https://example.com/tving.png', 'https://tving.com'),
(5, '쿠팡플레이', 'https://example.com/coupang.png', 'https://coupangplay.com');

-- 10. OTT Contents (OTT별 콘텐츠)
INSERT INTO ott_content (id, ott_provider_id, content_id, content_url) VALUES
(1, 1, 1, 'https://netflix.com/watch/눈물의여왕'),
(2, 4, 1, 'https://tving.com/watch/눈물의여왕'),
(3, 1, 2, 'https://netflix.com/watch/더글로리'),
(4, 1, 3, 'https://netflix.com/watch/우영우'),
(5, 4, 4, 'https://tving.com/watch/무빙'),
(6, 1, 5, 'https://netflix.com/watch/스위트홈');

-- 11. OTT Users (사용자 구독 OTT)
INSERT INTO ott_user (id, ott_provider_id, user_id) VALUES
(1, 1, 1),  -- 플리너원 - 넷플릭스
(2, 4, 1),  -- 플리너원 - 티빙
(3, 1, 2),  -- 일반유저 - 넷플릭스
(4, 2, 2),  -- 일반유저 - 왓챠
(5, 1, 3),  -- 콘텐츠매니아 - 넷플릭스
(6, 3, 3),  -- 콘텐츠매니아 - 웨이브
(7, 4, 3);  -- 콘텐츠매니아 - 티빙

-- 12. Collections (컬렉션)
INSERT INTO collection (id, title, description, collection_image, is_public, user_id, bookmark_count, created_at, updated_at) VALUES
(1, '힐링 드라마 모음', '지친 일상에 위로가 되는 드라마들', 'https://example.com/collection1.jpg', true, 1, 45, NOW(), NOW()),
(2, '반전 스릴러 추천', '마지막까지 긴장감 넘치는 작품들', 'https://example.com/collection2.jpg', true, 1, 67, NOW(), NOW()),
(3, '나만의 비밀 컬렉션', '아직 공개하고 싶지 않은 취향', 'https://example.com/collection3.jpg', false, 2, 0, NOW(), NOW()),
(4, '2024 상반기 Best', '올해 상반기 최고의 작품들', 'https://example.com/collection4.jpg', true, 3, 120, NOW(), NOW());

-- 13. Collection Contents (컬렉션 내 콘텐츠)
INSERT INTO collection_content (id, collection_id, content_id, is_spoiler, reason) VALUES
(1, 1, 3, false, '자폐를 가진 변호사의 따뜻한 성장기'),
(2, 1, 1, false, '재벌가의 사랑 이야기지만 힐링됨'),
(3, 2, 2, true, '복수극의 정점, 반전이 엄청남'),
(4, 2, 5, false, '괴물과의 생존, 긴장감 최고'),
(5, 3, 4, false, '초능력 액션이 시원함'),
(6, 4, 1, false, '2024 상반기 최고 화제작'),
(7, 4, 4, false, '웹툰 원작 중 가장 잘 만든 드라마');

-- 14. Content Bookmarks (콘텐츠 북마크)
INSERT INTO content_bookmark (id, user_id, content_id, created_at, updated_at) VALUES
(1, 1, 1, NOW(), NOW()),
(2, 1, 3, NOW(), NOW()),
(3, 2, 2, NOW(), NOW()),
(4, 2, 5, NOW(), NOW()),
(5, 3, 1, NOW(), NOW()),
(6, 3, 2, NOW(), NOW()),
(7, 3, 4, NOW(), NOW());

-- 15. Collection Bookmarks (컬렉션 북마크)
INSERT INTO collection_bookmark (id, user_id, collection_id) VALUES
(1, 2, 1),  -- 일반유저가 플리너원의 힐링 컬렉션 북마크
(2, 2, 2),  -- 일반유저가 플리너원의 스릴러 컬렉션 북마크
(3, 3, 1),  -- 콘텐츠매니아가 플리너원의 힐링 컬렉션 북마크
(4, 3, 4),  -- 콘텐츠매니아가 자신의 컬렉션 북마크
(5, 1, 4);  -- 플리너원이 콘텐츠매니아의 컬렉션 북마크

-- 16. Recent Viewed Collections (최근 본 컬렉션)
INSERT INTO recent_viewed_collection (id, user_id, collection_id, viewed_at, created_at, updated_at) VALUES
(1, 2, 1, NOW(), NOW(), NOW()),
(2, 2, 2, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
(3, 3, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW(), NOW()),
(4, 1, 4, DATE_SUB(NOW(), INTERVAL 30 MINUTE), NOW(), NOW());
```

---

## Enum 값 참고

### UserRole
- `ADMIN`: 관리자
- `FLINER`: 플리너 (큐레이터)
- `FLING`: 플링 (일반 사용자)

### UserStatus
- `ACTIVE`: 활성
- `WITHDRAWN`: 탈퇴

### AuthProvider
- `KAKAO`: 카카오
- `APPLE`: 애플

### KeywordLevel
- `L1`: 장르 (PINK)
- `L2`: 분위기/감정 (GREEN)
- `L3`: 서사/테마 (ORANGE)
- `L4`: 배경/문화권 (YELLOW)
- `L5`: 포맷 (BLUE)

### KeywordColor
- `PINK`: L1 장르
- `GREEN`: L2 분위기/감정
- `ORANGE`: L3 서사/테마
- `YELLOW`: L4 배경/문화권
- `BLUE`: L5 포맷

### Keyword Image (아이콘)
| Level | 이미지 | 설명 | 키워드 |
|-------|--------|------|--------|
| L1 | `pink_half_circle` | 분홍 반원/원형 블록 | 드라마, 코미디, 모험, 음악, 역사 |
| L1 | `pink_triangle` | 분홍 삼각형 패턴 | 액션, 범죄, 미스터리, 스릴러, 전쟁, 서부 |
| L1 | `pink_leaf` | 분홍 잎/꽃잎 모양 | 애니메이션, 판타지, SF, 로맨스 |
| L2 | `green_quarter_circle` | 초록 1/4원 형태 | 슬픈, 불안한 |
| L2 | `green_arrow_down` | 아래로 향한 V/화살표 | 어두운, 긴장되는, 잔혹한 |
| L2 | `green_clover` | 클로버/꽃잎 4개 | 잔잔한, 설레는, 몽환적인 |
| L2 | `green_star` | 초록 별/톱니 | 유쾌한 |
| L3 | `orange_droplet` | 물방울/둥근 D | 노년의 삶, 실화기반, 사회문제, 정체성, 권력 |
| L3 | `orange_pacman` | 팩맨처럼 파인 원형 | 오피스, 복수, 추리, 반전 중심 |
| L3 | `orange_semicircle_stack` | 반원 3단 적층 | 가족중심, 성장, 종교 |
| L3 | `orange_four_petals` | 4개 꽃잎/블록 | 브로맨스, 우정, 청춘, 생존 |
| L4 | `yellow_facing_semicircles` | 반원 2개 마주보는 형태 | 사극, 디스토피아, 근미래, 근대 |
| L4 | `yellow_four_circles` | 원 4개 묶음 | 한국, 북미, 유럽, 인도, 일본, 영국 |
| L5 | `blue_arch` | 파란 아치/∩ 형태 | 영화, 시리즈, 다큐멘터리, 예능 |
