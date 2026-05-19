package kr.flint.user.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
    name = "daily_visitor_activity",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_visitor_activity_key_date", columnNames = {"visitor_key_hash", "activity_date"})
    },
    indexes = {
        @Index(name = "idx_daily_visitor_activity_date", columnList = "activity_date")
    }
)
public class DailyVisitorActivity extends Base {

    @Column(nullable = false, length = 64)
    private String visitorKeyHash;

    private Long userId;

    @Column(nullable = false)
    private LocalDate activityDate;

    @Column(nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(nullable = false)
    private long requestCount;

    public static DailyVisitorActivity create(
        String visitorKeyHash,
        Long userId,
        LocalDate activityDate,
        LocalDateTime seenAt
    ) {
        return DailyVisitorActivity.builder()
            .visitorKeyHash(visitorKeyHash)
            .userId(userId)
            .activityDate(activityDate)
            .firstSeenAt(seenAt)
            .lastSeenAt(seenAt)
            .requestCount(1)
            .build();
    }
}
