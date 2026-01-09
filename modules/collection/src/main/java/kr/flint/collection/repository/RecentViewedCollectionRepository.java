package kr.flint.collection.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.collection.domain.RecentViewedCollection;

public interface RecentViewedCollectionRepository extends JpaRepository<RecentViewedCollection, Long> {
}
