package kr.flint.bookmark.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.flint.bookmark.domain.CollectionBookmark;

@Repository
public interface CollectionBookmarkRepository extends JpaRepository<CollectionBookmark, Long> {
	Optional<CollectionBookmark> findByCollectionIdAndUserId(Long collectionId, Long userId);
}
