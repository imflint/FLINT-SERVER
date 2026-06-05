package kr.flint.collection.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContentImage;

@Repository
public interface CollectionContentImageRepository extends JpaRepository<CollectionContentImage, Long> {
	@Modifying
	@Query("DELETE FROM CollectionContentImage image WHERE image.collectionContent.collection = :collection")
	void deleteAllByCollectionContentCollection(@Param("collection") Collection collection);
}
