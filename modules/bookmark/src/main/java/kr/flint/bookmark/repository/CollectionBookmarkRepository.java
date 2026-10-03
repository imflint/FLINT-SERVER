package kr.flint.bookmark.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.bookmark.domain.CollectionBookmark;

@Repository
public interface CollectionBookmarkRepository extends JpaRepository<CollectionBookmark, Long> {
	Optional<CollectionBookmark> findByCollectionIdAndUserId(Long collectionId, Long userId);

	@Query("""
		select cb.userId
		from CollectionBookmark cb
		where cb.collectionId = :collectionId
	""")
	List<Long> findUserIdsByCollectionId(@Param("collectionId") Long collectionId);

	@Query("""
		select cb.collectionId
		from CollectionBookmark cb
		where cb.userId = :userId
	""")
	List<Long> findCollectionIdsByUserId(@Param("userId") Long userId);

	@Query("""
		select cb.collectionId
		from CollectionBookmark cb
		where cb.userId = :userId
		  and cb.collectionId in :collectionIds
	""")
	List<Long> findCollectionIdsByUserIdAndCollectionIdIn(
		@Param("userId") Long userId,
		@Param("collectionIds") List<Long> collectionIds
	);

	int countByCollectionId(Long collectionId);

	void deleteAllByUserId(Long userId);
}
