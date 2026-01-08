package kr.flint.collection.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.Collection;

@Repository
public interface CollectionRepository extends CrudRepository<Collection, Long> {
}
