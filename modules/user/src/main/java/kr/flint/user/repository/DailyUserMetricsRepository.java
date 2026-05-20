package kr.flint.user.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.user.domain.DailyUserMetrics;

@Repository
public interface DailyUserMetricsRepository extends JpaRepository<DailyUserMetrics, Long> {

    Optional<DailyUserMetrics> findByMetricDate(LocalDate metricDate);

    boolean existsByMetricDate(LocalDate metricDate);

    @Query("""
        select m.metricDate as metricDate,
               m.visitorCount as visitorCount,
               m.signupUserCount as signupUserCount,
               m.memberCount as memberCount
        from DailyUserMetrics m
        where m.metricDate between :from and :to
        order by m.metricDate asc
    """)
    List<DailyUserMetricsProjection> findMetricsBetween(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Query("select min(m.metricDate) from DailyUserMetrics m")
    LocalDate findFirstMetricDate();

    interface DailyUserMetricsProjection {
        LocalDate getMetricDate();

        long getVisitorCount();

        long getSignupUserCount();

        long getMemberCount();
    }
}
