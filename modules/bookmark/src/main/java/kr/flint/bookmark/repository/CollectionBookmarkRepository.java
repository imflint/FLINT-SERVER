package kr.flint.bookmark.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	int countByCollectionId(Long collectionId);


		/*
		@Modifying : 결과를 반환하지 않고 DB를 직접 변경하는 쿼리(INSERT/UPDATE/DELETE)
		return: 영향 받은 row 수

		NativeQuery : JPA가 해석하거나 변환하지 않고 DB에 그대로 SQL 날림
		목적 : INSERT IGNORE, ON DUPLICATE KEY와 같이 동시성 제어
	 */

	@Modifying
	@Query(value = """
		DELETE FROM collection_bookmark
		WHERE user_id = :userId AND collection_id = :collectionId
	""", nativeQuery = true)
	int deleteCollectionBookmarkByUserIdAndCollectionId(@Param("userId")Long userId,
		@Param("collectionId") Long collectionId);

	@Modifying
	@Query(value = """
		INSERT IGNORE INTO collection_bookmark(id, user_id, collection_id)
		VALUES (:id, :userId, :collectionId)
""", nativeQuery = true)
	int insertIgnore(@Param("id") Long id, @Param("userId") Long userId, @Param("collectionId") Long collectionId);

	void deleteAllByUserId(Long userId);
}
