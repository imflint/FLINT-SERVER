package kr.flint.user.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.user.domain.DailyVisitorActivity;

@Repository
public interface DailyVisitorActivityRepository extends JpaRepository<DailyVisitorActivity, Long> {

    @Modifying
    @Query(value = """
        insert ignore into daily_visitor_activity
            (id, visitor_key_hash, user_id, activity_date, first_seen_at, last_seen_at, request_count)
        values
            (:id, :visitorKeyHash, :userId, :activityDate, :seenAt, :seenAt, 1)
    """, nativeQuery = true)
    int insertFirstVisit(
        @Param("id") Long id,
        @Param("visitorKeyHash") String visitorKeyHash,
        @Param("userId") Long userId,
        @Param("activityDate") LocalDate activityDate,
        @Param("seenAt") LocalDateTime seenAt
    );

    long countByActivityDate(LocalDate activityDate);

    @Query("select min(a.activityDate) from DailyVisitorActivity a")
    LocalDate findFirstActivityDate();
}
