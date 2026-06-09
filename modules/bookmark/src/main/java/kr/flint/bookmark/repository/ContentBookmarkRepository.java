package kr.flint.bookmark.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.bookmark.domain.CollectionBookmark;
import kr.flint.bookmark.domain.ContentBookmark;

@Repository
public interface ContentBookmarkRepository extends JpaRepository<ContentBookmark, Long> {
	Optional<ContentBookmark> findByContentIdAndUserId(Long id, Long userId);

	boolean existsByContentIdAndUserId(Long contentId, Long userId);

	int countByUserId(Long userId);

	@Query("""
		select cb.contentId
		from ContentBookmark cb
		where cb.userId = :userId
			and cb.contentId in :contentIds
	""")
	List<Long> findContentIdsByUserIdAndContentIdIn(
		@Param("userId") Long userId,
		@Param("contentIds") List<Long> contentIds
	);

	void deleteAllByUserId(Long userId);


}
