package kr.flint.taste.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.taste.domain.ContentKeyword;

@Repository
public interface ContentKeywordRepository extends JpaRepository<ContentKeyword, Long> {
    List<ContentKeyword> findByContentId(Long contentId);

    List<ContentKeyword> findByKeywordId(Long keywordId);
}
