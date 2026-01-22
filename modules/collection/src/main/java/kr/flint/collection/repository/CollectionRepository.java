package kr.flint.collection.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.dto.response.CollectionSummaryProjection;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, Long> {

    List<CollectionSummaryProjection> findByUserId(Long userId);

    List<CollectionSummaryProjection> findByIdIn(List<Long> ids);

	@Modifying
	@Query(value = """
		UPDATE collection
		SET bookmark_count = bookmark_count + 1
		WHERE id = :collectionId
""", nativeQuery = true)
	int incBookmarkCount(@Param("collectionId")Long collectionId);

	@Modifying
	@Query(value = """
        UPDATE collection
        SET bookmark_count = CASE WHEN bookmark_count > 0 THEN bookmark_count - 1 ELSE 0 END
        WHERE id = :collectionId
        """, nativeQuery = true)
	int decBookmarkCount(@Param("collectionId") Long collectionId);

	List<Collection> deleteAllByUserId(Long userId);

	List<Collection> findAllByUserId(Long userId);
}
