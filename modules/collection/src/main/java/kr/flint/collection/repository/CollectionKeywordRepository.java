package kr.flint.collection.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.CollectionKeyword;

@Repository
public interface CollectionKeywordRepository extends JpaRepository<CollectionKeyword, Long> {

    // 컬렉션의 키워드 ID 목록 조회
    @Query("SELECT ck.keywordId FROM CollectionKeyword ck WHERE ck.collectionId = :collectionId")
    List<Long> findKeywordIdsByCollectionId(@Param("collectionId") Long collectionId);

    // 여러 컬렉션의 키워드 조회 (배치용)
    @Query("SELECT ck FROM CollectionKeyword ck WHERE ck.collectionId IN :collectionIds")
    List<CollectionKeyword> findByCollectionIdIn(@Param("collectionIds") List<Long> collectionIds);

    // 컬렉션의 모든 키워드 삭제
    @Modifying
    @Query("DELETE FROM CollectionKeyword ck WHERE ck.collectionId = :collectionId")
    void deleteByCollectionId(@Param("collectionId") Long collectionId);

    // 특정 컬렉션의 특정 키워드들 삭제
    @Modifying
    @Query("DELETE FROM CollectionKeyword ck WHERE ck.collectionId = :collectionId AND ck.keywordId IN :keywordIds")
    void deleteByCollectionIdAndKeywordIdIn(
        @Param("collectionId") Long collectionId,
        @Param("keywordIds") List<Long> keywordIds
    );

    // 특정 컬렉션의 특정 키워드 존재 여부 확인
    boolean existsByCollectionIdAndKeywordId(Long collectionId, Long keywordId);
}
