package kr.flint.collection.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import kr.flint.collection.domain.CollectionReport;

public interface CollectionReportRepository extends JpaRepository<CollectionReport, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CollectionReport r where r.id = :reportId")
    Optional<CollectionReport> findByIdForUpdate(@Param("reportId") Long reportId);
}
