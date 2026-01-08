package kr.flint.collection.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.CollectionContent;

@Repository
public interface CollectionContentRepository extends JpaRepository<CollectionContent, Long> {
}
