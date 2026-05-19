package kr.flint.user.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.user.domain.DailyUserMetrics;
import kr.flint.user.dto.response.DailyUserMetricsRes;
import kr.flint.user.repository.DailyUserMetricsRepository;
import kr.flint.user.repository.DailyVisitorActivityRepository;
import kr.flint.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyUserMetricsAggregationService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyUserMetricsRepository dailyUserMetricsRepository;
    private final DailyVisitorActivityRepository dailyVisitorActivityRepository;
    private final UserRepository userRepository;

    @Transactional
    public DailyUserMetricsRes aggregateMetric(LocalDate metricDate) {
        LocalDateTime dayStart = metricDate.atStartOfDay();
        LocalDateTime nextDayStart = metricDate.plusDays(1).atStartOfDay();
        long visitorCount = dailyVisitorActivityRepository.countByActivityDate(metricDate);
        long signupUserCount = userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            dayStart,
            nextDayStart
        );
        long memberCount = userRepository.countByCreatedAtLessThan(nextDayStart);
        LocalDateTime aggregatedAt = LocalDateTime.now(SEOUL_ZONE);

        DailyUserMetrics metrics = dailyUserMetricsRepository.findByMetricDate(metricDate)
            .map(existing -> {
                existing.replaceCounts(visitorCount, signupUserCount, memberCount, aggregatedAt);
                return existing;
            })
            .orElseGet(() -> dailyUserMetricsRepository.save(DailyUserMetrics.create(
                metricDate,
                visitorCount,
                signupUserCount,
                memberCount,
                aggregatedAt
            )));

        return DailyUserMetricsRes.of(
            metrics.getMetricDate(),
            metrics.getVisitorCount(),
            metrics.getSignupUserCount(),
            metrics.getMemberCount()
        );
    }

    @Transactional
    public void aggregateMissingMetricsUntil(LocalDate endDate) {
        Optional<LocalDate> firstSourceDate = findFirstSourceDate();
        if (firstSourceDate.isEmpty() || firstSourceDate.get().isAfter(endDate)) {
            return;
        }

        firstSourceDate.get().datesUntil(endDate.plusDays(1))
            .filter(date -> !dailyUserMetricsRepository.existsByMetricDate(date))
            .forEach(this::aggregateMetric);
    }

    public List<DailyUserMetricsRes> getDailyMetrics(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return List.of();
        }

        Map<LocalDate, DailyUserMetricsRepository.DailyUserMetricsProjection> metricsByDate =
            dailyUserMetricsRepository.findMetricsBetween(from, to).stream()
                .collect(Collectors.toMap(
                    DailyUserMetricsRepository.DailyUserMetricsProjection::getMetricDate,
                    Function.identity()
                ));

        return from.datesUntil(to.plusDays(1))
            .map(date -> {
                DailyUserMetricsRepository.DailyUserMetricsProjection metrics = metricsByDate.get(date);
                if (metrics == null) {
                    return DailyUserMetricsRes.of(date, 0, 0, 0);
                }
                return DailyUserMetricsRes.of(
                    date,
                    metrics.getVisitorCount(),
                    metrics.getSignupUserCount(),
                    metrics.getMemberCount()
                );
            })
            .toList();
    }

    public Optional<LocalDate> findFirstMetricDate() {
        return Optional.ofNullable(dailyUserMetricsRepository.findFirstMetricDate());
    }

    private Optional<LocalDate> findFirstSourceDate() {
        LocalDate firstSignupDate = Optional.ofNullable(userRepository.findFirstCreatedAt())
            .map(LocalDateTime::toLocalDate)
            .orElse(null);
        LocalDate firstActivityDate = dailyVisitorActivityRepository.findFirstActivityDate();

        if (firstSignupDate == null && firstActivityDate == null) {
            return Optional.empty();
        }

        if (firstSignupDate == null) {
            return Optional.of(firstActivityDate);
        }

        if (firstActivityDate == null) {
            return Optional.of(firstSignupDate);
        }

        return Optional.of(firstSignupDate.isBefore(firstActivityDate) ? firstSignupDate : firstActivityDate);
    }
}
