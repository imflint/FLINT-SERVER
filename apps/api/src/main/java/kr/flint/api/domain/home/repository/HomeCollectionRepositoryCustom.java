package kr.flint.api.domain.home.repository;

import java.util.List;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import kr.flint.api.domain.home.dto.projection.CollectionCardProjection;

public interface HomeCollectionRepositoryCustom {

    List<CollectionCardProjection> findCollectionCardsWithUser(List<Long> collectionIds);

    List<Long> findAllFlinerIds();

    List<CollectionBasicProjection> findPublicCollectionsByFlinerIds(List<Long> flinerIds);
}
