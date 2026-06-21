package kr.flint.api.domain.home.repository;

import java.time.LocalDateTime;
import java.util.List;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import kr.flint.api.domain.home.dto.projection.CollectionCardDto;
import kr.flint.api.domain.home.dto.projection.CollectionContentImageDto;

public interface HomeCollectionRepositoryCustom {

    List<CollectionCardDto> findCollectionCardsWithUser(List<Long> collectionIds);

    List<CollectionContentImageDto> findContentImagesByCollectionIds(List<Long> collectionIds);

    List<Long> findAllFlinerIds();

    List<CollectionBasicProjection> findPublicCollectionsByFlinerIds(List<Long> flinerIds);

    // 인기순(북마크 수) 공개 컬렉션 ID 조회
    List<Long> findPopularPublicCollectionIds(int limit);

    // 최근 N일 북마크 증가량(없으면 총 북마크 수 fallback) 기준 인기 컬렉션 ID 조회
    List<Long> findWeeklyPopularPublicCollectionIds(LocalDateTime since, int limit);
}
