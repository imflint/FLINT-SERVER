package kr.flint.collection.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.collection.domain.CollectionReport;

public interface CollectionReportRepository extends JpaRepository<CollectionReport, Long> {
}
