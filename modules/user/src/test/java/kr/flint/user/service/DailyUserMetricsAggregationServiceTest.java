package kr.flint.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.user.domain.DailyUserMetrics;
import kr.flint.user.dto.response.DailyUserMetricsRes;
import kr.flint.user.repository.DailyUserMetricsRepository;
import kr.flint.user.repository.DailyVisitorActivityRepository;
import kr.flint.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DailyUserMetricsAggregationServiceTest {

    @Mock
    private DailyUserMetricsRepository dailyUserMetricsRepository;

    @Mock
    private DailyVisitorActivityRepository dailyVisitorActivityRepository;

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<DailyUserMetrics> dailyUserMetricsCaptor;

    @InjectMocks
    private DailyUserMetricsAggregationService dailyUserMetricsAggregationService;

    @Nested
    @DisplayName("aggregateMetric")
    class AggregateMetric {

        @Test
        @DisplayName("특정 날짜의 방문자, 신규 가입, 전체 회원 수를 집계 row로 생성")
        void createDailyMetrics() {
            // given
            LocalDate date = LocalDate.of(2026, 5, 18);
            when(dailyVisitorActivityRepository.countByActivityDate(date)).thenReturn(4L);
            when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDateTime.of(2026, 5, 18, 0, 0),
                LocalDateTime.of(2026, 5, 19, 0, 0)
            )).thenReturn(2L);
            when(userRepository.countByCreatedAtLessThan(LocalDateTime.of(2026, 5, 19, 0, 0))).thenReturn(10L);
            when(dailyUserMetricsRepository.findByMetricDate(date)).thenReturn(Optional.empty());
            when(dailyUserMetricsRepository.save(dailyUserMetricsCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0, DailyUserMetrics.class));

            // when
            DailyUserMetricsRes result = dailyUserMetricsAggregationService.aggregateMetric(date);

            // then
            DailyUserMetrics saved = dailyUserMetricsCaptor.getValue();
            assertThat(saved.getMetricDate()).isEqualTo(date);
            assertThat(saved.getVisitorCount()).isEqualTo(4);
            assertThat(saved.getSignupUserCount()).isEqualTo(2);
            assertThat(saved.getMemberCount()).isEqualTo(10);
            assertThat(saved.getLastAggregatedAt()).isNotNull();
            assertThat(result).isEqualTo(DailyUserMetricsRes.of(date, 4, 2, 10));
        }

        @Test
        @DisplayName("기존 집계 row가 있으면 값을 교체")
        void updateExistingDailyMetrics() {
            // given
            LocalDate date = LocalDate.of(2026, 5, 18);
            DailyUserMetrics existing = DailyUserMetrics.create(
                date,
                1,
                1,
                1,
                LocalDateTime.of(2026, 5, 18, 1, 0)
            );
            when(dailyVisitorActivityRepository.countByActivityDate(date)).thenReturn(4L);
            when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDateTime.of(2026, 5, 18, 0, 0),
                LocalDateTime.of(2026, 5, 19, 0, 0)
            )).thenReturn(2L);
            when(userRepository.countByCreatedAtLessThan(LocalDateTime.of(2026, 5, 19, 0, 0))).thenReturn(10L);
            when(dailyUserMetricsRepository.findByMetricDate(date)).thenReturn(Optional.of(existing));

            // when
            DailyUserMetricsRes result = dailyUserMetricsAggregationService.aggregateMetric(date);

            // then
            assertThat(existing.getVisitorCount()).isEqualTo(4);
            assertThat(existing.getSignupUserCount()).isEqualTo(2);
            assertThat(existing.getMemberCount()).isEqualTo(10);
            assertThat(result).isEqualTo(DailyUserMetricsRes.of(date, 4, 2, 10));
        }
    }

    @Nested
    @DisplayName("aggregateMissingMetricsUntil")
    class AggregateMissingMetricsUntil {

        @Test
        @DisplayName("최초 소스 날짜부터 종료일까지 누락된 날짜만 집계")
        void aggregateMissingDates() {
            // given
            LocalDate from = LocalDate.of(2026, 5, 17);
            LocalDate to = LocalDate.of(2026, 5, 18);
            when(userRepository.findFirstCreatedAt()).thenReturn(LocalDateTime.of(2026, 5, 17, 12, 0));
            when(dailyVisitorActivityRepository.findFirstActivityDate()).thenReturn(LocalDate.of(2026, 5, 18));
            when(dailyUserMetricsRepository.existsByMetricDate(from)).thenReturn(true);
            when(dailyUserMetricsRepository.existsByMetricDate(to)).thenReturn(false);
            when(dailyVisitorActivityRepository.countByActivityDate(to)).thenReturn(3L);
            when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDateTime.of(2026, 5, 18, 0, 0),
                LocalDateTime.of(2026, 5, 19, 0, 0)
            )).thenReturn(1L);
            when(userRepository.countByCreatedAtLessThan(LocalDateTime.of(2026, 5, 19, 0, 0))).thenReturn(11L);
            when(dailyUserMetricsRepository.findByMetricDate(to)).thenReturn(Optional.empty());
            when(dailyUserMetricsRepository.save(dailyUserMetricsCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0, DailyUserMetrics.class));

            // when
            dailyUserMetricsAggregationService.aggregateMissingMetricsUntil(to);

            // then
            DailyUserMetrics saved = dailyUserMetricsCaptor.getValue();
            assertThat(saved.getMetricDate()).isEqualTo(to);
            assertThat(saved.getVisitorCount()).isEqualTo(3);
            assertThat(saved.getSignupUserCount()).isEqualTo(1);
            assertThat(saved.getMemberCount()).isEqualTo(11);
            verify(dailyUserMetricsRepository).existsByMetricDate(from);
            verify(dailyUserMetricsRepository).existsByMetricDate(to);
        }
    }

    @Nested
    @DisplayName("getDailyMetrics")
    class GetDailyMetrics {

        @Test
        @DisplayName("집계 row가 없는 날짜는 0으로 채움")
        void fillMissingDates() {
            // given
            LocalDate from = LocalDate.of(2026, 5, 17);
            LocalDate to = LocalDate.of(2026, 5, 18);
            when(dailyUserMetricsRepository.findMetricsBetween(from, to)).thenReturn(List.of(
                new MetricsProjection(LocalDate.of(2026, 5, 18), 4, 2, 10)
            ));

            // when
            List<DailyUserMetricsRes> result = dailyUserMetricsAggregationService.getDailyMetrics(from, to);

            // then
            assertThat(result).containsExactly(
                DailyUserMetricsRes.of(LocalDate.of(2026, 5, 17), 0, 0, 0),
                DailyUserMetricsRes.of(LocalDate.of(2026, 5, 18), 4, 2, 10)
            );
        }
    }

    private record MetricsProjection(
        LocalDate metricDate,
        long visitorCount,
        long signupUserCount,
        long memberCount
    ) implements DailyUserMetricsRepository.DailyUserMetricsProjection {

        @Override
        public LocalDate getMetricDate() {
            return metricDate;
        }

        @Override
        public long getVisitorCount() {
            return visitorCount;
        }

        @Override
        public long getSignupUserCount() {
            return signupUserCount;
        }

        @Override
        public long getMemberCount() {
            return memberCount;
        }
    }
}
