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
    name = "daily_user_metrics",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_user_metrics_date", columnNames = "metric_date")
    },
    indexes = {
        @Index(name = "idx_daily_user_metrics_date", columnList = "metric_date")
    }
)
public class DailyUserMetrics extends Base {

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(nullable = false)
    private long visitorCount;

    @Column(nullable = false)
    private long signupUserCount;

    @Column(nullable = false)
    private long memberCount;

    @Column(nullable = false)
    private LocalDateTime lastAggregatedAt;

    public static DailyUserMetrics create(
        LocalDate metricDate,
        long visitorCount,
        long signupUserCount,
        long memberCount,
        LocalDateTime aggregatedAt
    ) {
        return DailyUserMetrics.builder()
            .metricDate(metricDate)
            .visitorCount(visitorCount)
            .signupUserCount(signupUserCount)
            .memberCount(memberCount)
            .lastAggregatedAt(aggregatedAt)
            .build();
    }

    public void replaceCounts(
        long visitorCount,
        long signupUserCount,
        long memberCount,
        LocalDateTime aggregatedAt
    ) {
        this.visitorCount = visitorCount;
        this.signupUserCount = signupUserCount;
        this.memberCount = memberCount;
        this.lastAggregatedAt = aggregatedAt;
    }
}
