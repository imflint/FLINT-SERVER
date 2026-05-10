package kr.flint.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;

public interface ContentRepository extends JpaRepository<Content, Long> {
	boolean existsByTitle(String title);

	Optional<Content> findByTitle(String title);

	List<Content> findContentsByTitleContaining(String title);

	boolean existsByTmdbIdAndMediaType(Long tmdbId, MediaType mediaType);

	Optional<Content> findByTmdbIdAndMediaType(Long tmdbId, MediaType mediaType);

	List<Content> findAllByTitleContaining(String title);

	// limit 적용 메서드
	List<Content> findAllByTitleContaining(String title, Pageable pageable);

	List<Content> findAllByMediaTypeOrderByIdAsc(MediaType mediaType, Pageable pageable);

	List<Content> findAllByOrderByCreatedAtDesc(Pageable pageable);

	@Modifying
	@Query(value = """
		UPDATE content
		SET bookmark_count = bookmark_count + 1
		WHERE id = :contentId
""", nativeQuery = true)
	int incBookmarkCount(@Param("contentId")Long contentId);
}
