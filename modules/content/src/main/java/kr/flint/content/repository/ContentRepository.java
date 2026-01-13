package kr.flint.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.content.domain.Content;

public interface ContentRepository extends JpaRepository<Content, Long> {
	boolean existsByTitle(String title);

	Optional<Content> findByTitle(String title);

	List<Content> findContentsByTitleContaining(String title);

	boolean existsByTmdbId(Long tmdbId);

	Optional<Content> findContentByTmdbId(Long tmdbId);
}
