package kr.flint.api.domain.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.Collection;

@Repository
public interface HomeCollectionRepository extends JpaRepository<Collection, Long>, HomeCollectionRepositoryCustom {
    // QueryDSL CustomImpl로 모든 쿼리 이동
}
