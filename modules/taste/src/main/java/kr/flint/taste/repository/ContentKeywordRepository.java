package kr.flint.taste.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.taste.domain.ContentKeyword;

@Repository
public interface ContentKeywordRepository extends JpaRepository<ContentKeyword, Long> {
    List<ContentKeyword> findByContentId(Long contentId);

    List<ContentKeyword> findByKeywordId(Long keywordId);

    // 콘텐츠의 키워드 ID 목록 조회
    @Query("SELECT ck.keywordId FROM ContentKeyword ck WHERE ck.contentId = :contentId")
    List<Long> findKeywordIdsByContentId(@Param("contentId") Long contentId);

    // 여러 콘텐츠의 키워드 ID 목록 조회 (중복 제거)
    @Query("SELECT DISTINCT ck.keywordId FROM ContentKeyword ck WHERE ck.contentId IN :contentIds")
    List<Long> findKeywordIdsByContentIdIn(@Param("contentIds") List<Long> contentIds);
}
