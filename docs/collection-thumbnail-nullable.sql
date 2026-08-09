-- 컬렉션 대표 이미지가 없는 경우 null 저장을 허용한다.
-- 애플리케이션 배포 전에 운영 DB에 적용한다.

ALTER TABLE collection
    MODIFY COLUMN collection_image VARCHAR(255) NULL;
