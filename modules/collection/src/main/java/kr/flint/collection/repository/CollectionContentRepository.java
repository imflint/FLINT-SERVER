package kr.flint.collection.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContent;

@Repository
public interface CollectionContentRepository extends JpaRepository<CollectionContent, Long> {
	List<CollectionContent> findAllByCollection(Collection collection);

	// 컬렉션의 콘텐츠 ID 목록 조회
	@Query("SELECT cc.contentId FROM CollectionContent cc WHERE cc.collection.id = :collectionId")
	List<Long> findContentIdsByCollectionId(@Param("collectionId") Long collectionId);
}
