package kr.flint.api.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.Collection;

@Repository
public interface UserCollectionRepository extends JpaRepository<Collection, Long>, UserCollectionRepositoryCustom {
}
