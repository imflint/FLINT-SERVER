package kr.flint.collection.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.flint.collection.domain.RecentViewedCollection;

public interface RecentViewedCollectionRepository extends JpaRepository<RecentViewedCollection, Long> {
	@Modifying
	@Query(value = """
		INSERT INTO recent_viewed_collection (
			id,
			user_id,
			collection_id,
			viewed_at,
			created_at,
			updated_at
		)
		VALUES (
			:id,
			:userId,
			:collectionId,
			:viewedAt,
			:viewedAt,
			:viewedAt
		)
		ON DUPLICATE KEY UPDATE
			viewed_at = VALUES(viewed_at),
			updated_at = VALUES(updated_at)
	""", nativeQuery = true)
	int upsertRecentViewedCollection(
		@Param("id") Long id,
		@Param("userId") Long userId,
		@Param("collectionId") Long collectionId,
		@Param("viewedAt") LocalDateTime viewedAt
	);

	void deleteAllByUserId(Long userId);
}
