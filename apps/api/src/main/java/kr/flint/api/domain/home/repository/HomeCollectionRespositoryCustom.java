package kr.flint.api.domain.home.repository;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HomeCollectionRespositoryCustom {
    List<CollectionBasicProjection> findPublicCollectionsByFlinerIds(@Param("flinerIds") List<Long> flinerIds);


}
