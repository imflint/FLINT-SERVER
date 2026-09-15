# 플랫폼 API 통합 및 TMDB 카탈로그 전환 Runbook

## 적용 범위

- 사용자 API와 관리자 API를 동일한 `apps/api` 프로세스와 `https://flint.r-e.kr` 호스트에서 제공한다.
- 관리자 프론트 S3·CloudFront는 유지한다.
- TMDB 카탈로그는 한글 또는 영문 제목이 확인된 작품만 유지한다.
- 이 문서는 운영 순서를 정의하며 자동으로 RDS 스냅샷이나 Terraform apply를 실행하지 않는다.

## 사전 조건

1. 현재 RDS 자동 백업과 수동 스냅샷 생성 권한을 확인한다.
2. 플랫폼 EC2가 `t4g.small`이고 API JVM에 batch 실행 여유가 있는지 확인한다.
3. TMDB API key, S3, Redis, DB Parameter Store 설정이 플랫폼 API 역할에서 조회 가능한지 확인한다.
4. 관리자 프론트의 새 API base URL을 `https://flint.r-e.kr`로 변경할 준비를 한다.
5. 기존 관리자 EC2는 전체 전환과 smoke test가 끝날 때까지 유지한다.

## 1. DDL

`docs/tmdb-catalog-platform-consolidation.sql`을 항목별로 적용한다. 이 SQL은 재실행용 migration이 아니므로 각 컬럼과 인덱스 존재 여부를 먼저 확인한다.

DDL 직후 확인한다.

- `admin.token_valid_after`가 모든 관리자 행에 존재한다.
- `content` localized title 컬럼과 `ft_content_search_title_ngram`이 존재한다.
- `tmdb_catalog_entry`의 기존 콘텐츠가 모두 `PENDING`으로 seed됐다.
- `tmdb_sync_run`, `tmdb_sync_lock`, prune manifest, S3 delete queue 테이블이 존재한다.
- 기존 title FULLTEXT 인덱스는 아직 제거하지 않는다.

## 2. 호환 배포

1. `FLINT_LOCALIZED_SEARCH_ENABLED=false`로 플랫폼 API를 배포한다.
2. prod의 `flint.batch.scheduling.enabled`는 `false` 상태로 유지한다.
3. 기존 사용자 API smoke test를 수행한다.
4. `/api/v1/admin/auth/login`에서 새 ADMIN 토큰을 발급한다.
5. 전환 이전 ADMIN Access/Refresh Token이 모두 거부되는지 확인한다.
6. 관리자 조회·수정 API를 플랫폼 호스트에서 확인한다.

## 3. 분류 실행

dev 프로필에서 다음 순서로 실행한다.

1. `POST /api/v1/admin/batch/language-cleanup/classify`
2. `GET /api/v1/admin/batch/runs/{runId}`로 완료 상태와 진행률 확인
3. `PENDING`, `RETRY`, registry 미등록 콘텐츠가 0이 될 때까지 일시 오류를 재처리

`CLASSIFY_ONLY`는 `content`와 사용자 관계를 직접 수정하지 않는다. TMDB에서 확정한 한글·영문 제목과 정규화 검색 문자열은 `tmdb_catalog_entry`에 staging하며, cleanup execute의 삭제가 끝난 뒤 남은 콘텐츠에 반영한다.

초기 약 107만 건 상세 검증은 전역 5req/s 제한에서 이론상 최소 약 60시간이 필요하다. 배포 중단 시 Job은 `STOPPED`가 되고 같은 업무 키를 다시 요청하면 Spring Batch checkpoint부터 재개한다.

중단 조건:

- TMDB 429·5xx·timeout 비율이 지속 증가
- 플랫폼 API 메모리 또는 CPU 포화
- DB connection pool 고갈
- run lease heartbeat 중단 또는 동일 업무 키 중복 실행

## 4. 삭제 Preview와 실행

1. DDL 문서의 readiness query가 모두 허용 상태인지 확인한다.
2. `POST /api/v1/admin/batch/language-cleanup/preview`를 호출한다.
3. 응답의 manifest ID, 후보 건수, SHA-256 후보 해시를 변경 승인 기록에 남긴다.
4. preview 이후 RDS 수동 스냅샷을 생성하고 `available` 상태까지 기다린다.
5. manifest ID·해시와 `snapshotConfirmed=true`로 execute를 호출한다.
6. 중단 시 동일 manifest로 execute를 재호출한다. `EXECUTING` 상태는 처리하지 못한 500건 chunk부터 재개한다.
7. 500건 chunk 삭제와 localized-title staging 반영 완료 후 orphan, 활성 빈 컬렉션, 잔존 부적격 콘텐츠를 다시 확인한다.

삭제 순서는 다음과 같다.

`collection_content_images → collection_content → content_bookmark → ott_content → content_keywords → content_genre → content`

작품이 없어진 컬렉션은 soft-delete한다. 남은 작품 순서를 다시 0부터 정렬하고, 컬렉션 키워드와 추천 Redis 캐시를 갱신한다. `collection/content/` S3 key만 대기열에 남으며 실제 삭제는 30일 후 수행된다.

## 5. Localized 검색 전환

1. 모든 유지 콘텐츠에 `title_ko` 또는 `title_en`이 존재하는지 확인한다.
2. `MATCH(search_title) AGAINST(...)`의 실행 계획과 대표 검색어 결과를 확인한다.
3. `FLINT_LOCALIZED_SEARCH_ENABLED=true`로 dev를 먼저 배포한다.
4. 한글/영문, 공백·특수문자·대소문자 변형, 완전 일치·관련도 순서, cursor 다음 페이지를 검증한다.
5. prod flag를 전환한 뒤 오류율과 지연시간을 관찰한다.
6. 안정화 후에만 실제 이름을 확인한 기존 title FULLTEXT 인덱스를 제거한다.

## 6. OTT와 스케줄 검증

1. 카탈로그 DDL을 적용하고 prod 자동 스케줄은 비활성 상태로 배포한다.
2. 대표 Movie/TV 작품만 `next_refresh_at`을 현재 시각 이전으로 지정한 뒤 `POST /api/v1/admin/batch/daily-sync`를 호출한다. 실행 시작 시 Movie/TV provider master가 `watch_region=KR` 합집합으로 먼저 동기화된다.
3. 기존 6개 provider의 내부 PK와 사용자 구독 FK가 유지되는지 확인한다.
4. 대표 작품의 TMDB `KR.flatrate`, `ott_content`, `GET /api/v1/contents/ott/{contentId}` 결과를 비교한다. API는 사용자 구독과 무관하게 활성 국내 정액제 OTT 전체를 반환해야 한다.
5. 콘텐츠 상세 200 응답은 OTT 관계 전체를 교체하고 빈 `flatrate`면 관계를 비우며, 429·5xx·timeout은 기존 관계를 보존하는지 확인한다.
6. canary가 통과하면 `SYNCED` 작품의 `next_refresh_at`을 현재 시각 이전으로 옮기고 일간 동기화를 실행해 5req/s, 동시성 3, chunk 50으로 전체 백필한다. 약 107만 건이면 최소 약 60시간을 예상하고 실행 목록의 실패·재시도·처리량을 관찰한다.
7. Watch Provider 데이터가 보이는 클라이언트 화면에 JustWatch 출처를 표시한다.
8. dev에서 월간 02:00 KST, 일간 05:00 KST 실행과 월간 우선 lease를 확인한 후에만 prod 자동 스케줄 활성화를 별도 승인한다.

canary와 전체 백필 대상은 실행 전에 반드시 건수를 확인한다.

```sql
-- Canary: 승인한 대표 TMDB ID만 due 상태로 전환한다.
SELECT media_type, tmdb_id, status, next_refresh_at
FROM tmdb_catalog_entry
WHERE (media_type, tmdb_id) IN (('MOVIE', <movie_tmdb_id>), ('TV', <tv_tmdb_id>));

UPDATE tmdb_catalog_entry
SET next_refresh_at = UTC_TIMESTAMP(6)
WHERE status = 'SYNCED'
  AND (media_type, tmdb_id) IN (('MOVIE', <movie_tmdb_id>), ('TV', <tv_tmdb_id>));

-- Full backfill preview.
SELECT COUNT(*) AS due_backfill_count
FROM tmdb_catalog_entry
WHERE status = 'SYNCED';

-- Execute only after canary approval and capacity confirmation.
UPDATE tmdb_catalog_entry
SET next_refresh_at = UTC_TIMESTAMP(6)
WHERE status = 'SYNCED';
```

## 7. 관리자 전환과 인프라 폐기

관리자 프론트에서 플랫폼 호스트 기준으로 다음 smoke test를 수행한다.

- 로그인과 refresh
- 관리자 본인 조회·수정
- 사용자 조회·제재
- 콘텐츠·컬렉션 조회·수정
- 신고 처리
- 약관 조회·등록·수정
- 배치 실행 목록·상세 조회와 수동 실행

모두 통과한 뒤 기존 관리자 서버 트래픽을 중단한다. Terraform plan에서 관리자 EC2, EIP, SG, ECR, IAM, DNS만 destroy되고 관리자 프론트 S3·CloudFront는 유지되는지 검토한 후 apply한다. ECR 이미지나 기존 서버 로그 보존이 필요하면 destroy 전에 별도 보관한다.

## 8. 완료 조건

- 언어 부적격 `content` 0건
- 모든 콘텐츠의 `title_ko/title_en` 중 하나 이상 존재
- 콘텐츠 연결 테이블 orphan 0건
- 활성 빈 컬렉션 0건
- 동일 업무 키 중복 실행 0건
- 관리자 프론트의 플랫폼 호스트 smoke test 통과
- 기존 관리자 API 인프라 제거 후 사용자·관리자 API health 정상
