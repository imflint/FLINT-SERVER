package kr.flint.collection.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.dto.response.CollectionSummaryProjection;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, Long> {

    List<CollectionSummaryProjection> findByUserId(Long userId);

    List<CollectionSummaryProjection> findByIdIn(List<Long> ids);
}
