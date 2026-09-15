-- 컬렉션 soft delete 전환 백필
-- 배포 전 기존 moderation_status = 'DELETED' 데이터를 deleted_at으로 이전한 뒤 enum 값을 정리한다.
-- deleted_at 컬럼이 이미 생성되어 있다면 ALTER TABLE은 건너뛴다.

ALTER TABLE collection
    ADD COLUMN deleted_at DATETIME(6) NULL;

UPDATE collection
SET deleted_at = COALESCE(updated_at, created_at, NOW(6))
WHERE moderation_status = 'DELETED'
  AND deleted_at IS NULL;

UPDATE collection
SET moderation_status = 'VISIBLE'
WHERE moderation_status = 'DELETED';
