package kr.flint.api.domain.home.repository;

import java.util.List;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import kr.flint.api.domain.home.dto.projection.CollectionCardDto;
import kr.flint.api.domain.home.dto.projection.CollectionContentImageDto;

public interface HomeCollectionRepositoryCustom {

    List<CollectionCardDto> findCollectionCardsWithUser(List<Long> collectionIds);

    List<CollectionContentImageDto> findContentImagesByCollectionIds(List<Long> collectionIds);

    List<Long> findAllFlinerIds();

    List<CollectionBasicProjection> findPublicCollectionsByFlinerIds(List<Long> flinerIds);
}
