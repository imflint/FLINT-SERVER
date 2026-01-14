package kr.flint.bookmark.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.bookmark.domain.CollectionBookmark;
import kr.flint.bookmark.domain.ContentBookmark;

@Repository
public interface ContentBookmarkRepository extends JpaRepository<ContentBookmark, Long> {
	Optional<ContentBookmark> findByContentIdAndUserId(Long id, Long userId);

	List<ContentBookmark> findAllByUserId(Long userId);
}
