package kr.flint.taste.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.taste.domain.CollectionKeyword;

@Repository
public interface CollectionKeywordRepository
    extends JpaRepository<CollectionKeyword, Long>, CollectionKeywordRepositoryCustom {

    @Query("SELECT ck.keywordId FROM CollectionKeyword ck WHERE ck.collectionId = :collectionId")
    List<Long> findKeywordIdsByCollectionId(@Param("collectionId") Long collectionId);

    @Query("SELECT ck FROM CollectionKeyword ck WHERE ck.collectionId IN :collectionIds")
    List<CollectionKeyword> findByCollectionIdIn(@Param("collectionIds") List<Long> collectionIds);

    boolean existsByCollectionIdAndKeywordId(Long collectionId, Long keywordId);
}
