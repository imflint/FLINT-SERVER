package kr.flint.collection.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.Collection;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, Long> {
}
