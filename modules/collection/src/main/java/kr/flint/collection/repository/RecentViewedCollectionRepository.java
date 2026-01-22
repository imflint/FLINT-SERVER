package kr.flint.collection.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.RecentViewedCollection;

public interface RecentViewedCollectionRepository extends JpaRepository<RecentViewedCollection, Long> {
	Optional<RecentViewedCollection> findByUserIdAndCollection(Long userId, Collection collection);

	void deleteAllByUserId(Long userId);
}
