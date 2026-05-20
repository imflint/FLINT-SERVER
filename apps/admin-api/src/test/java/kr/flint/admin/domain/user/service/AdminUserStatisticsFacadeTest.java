package kr.flint.admin.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.user.dto.request.AdminDailyUserMetricsRange;
import kr.flint.admin.domain.user.dto.response.AdminDailyUserMetricsRes;
import kr.flint.adminauth.exception.AdminErrorCode;
import kr.flint.adminauth.exception.AdminException;
import kr.flint.user.dto.response.DailyUserMetricsRes;
import kr.flint.user.service.DailyUserMetricsAggregationService;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class AdminUserStatisticsFacadeTest {

    @Mock
    private AdminAuthorizationService adminAuthorizationService;

    @Mock
    private UserService userService;

    @Mock
    private DailyUserMetricsAggregationService dailyUserMetricsAggregationService;

    @InjectMocks
    private AdminUserStatisticsFacade adminUserStatisticsFacade;

    @Nested
    @DisplayName("getDailyActivity")
    class GetDailyActivity {

        @Test
        @DisplayName("집계 테이블의 일별 지표를 응답 DTO로 변환")
        void convertDailyMetrics() {
            // given
            LocalDate from = LocalDate.of(2026, 5, 17);
            LocalDate to = LocalDate.of(2026, 5, 18);
            when(dailyUserMetricsAggregationService.getDailyMetrics(from, to)).thenReturn(List.of(
                DailyUserMetricsRes.of(LocalDate.of(2026, 5, 17), 4, 2, 10),
                DailyUserMetricsRes.of(LocalDate.of(2026, 5, 18), 6, 3, 13)
            ));

            // when
            AdminDailyUserMetricsRes result = adminUserStatisticsFacade.getDailyActivity(1L, AdminDailyUserMetricsRange.DAYS_30, from, to);

            // then
            assertThat(result.dailyMetrics()).containsExactly(
                AdminDailyUserMetricsRes.DailyUserMetricRes.of(LocalDate.of(2026, 5, 17), 4, 2, 10),
                AdminDailyUserMetricsRes.DailyUserMetricRes.of(LocalDate.of(2026, 5, 18), 6, 3, 13)
            );
        }

        @Test
        @DisplayName("7일 선택 시 기준일 포함 최근 7일을 조회")
        void resolveSevenDaysRange() {
            // given
            LocalDate to = LocalDate.of(2026, 5, 18);
            LocalDate expectedFrom = LocalDate.of(2026, 5, 12);
            when(dailyUserMetricsAggregationService.getDailyMetrics(expectedFrom, to)).thenReturn(List.of());

            // when
            adminUserStatisticsFacade.getDailyActivity(1L, AdminDailyUserMetricsRange.DAYS_7, null, to);

            // then
            verify(dailyUserMetricsAggregationService).getDailyMetrics(expectedFrom, to);
        }

        @Test
        @DisplayName("전체 선택 시 가장 오래된 집계 날짜부터 조회")
        void resolveAllRange() {
            // given
            LocalDate to = LocalDate.of(2026, 5, 18);
            LocalDate expectedFrom = LocalDate.of(2026, 4, 20);
            when(dailyUserMetricsAggregationService.findFirstMetricDate()).thenReturn(Optional.of(expectedFrom));
            when(dailyUserMetricsAggregationService.getDailyMetrics(expectedFrom, to)).thenReturn(List.of());

            // when
            adminUserStatisticsFacade.getDailyActivity(1L, AdminDailyUserMetricsRange.ALL, null, to);

            // then
            verify(dailyUserMetricsAggregationService).getDailyMetrics(expectedFrom, to);
        }

        @Test
        @DisplayName("관리자 검증에 실패하면 조회하지 않음")
        void adminValidationFailure() {
            // given
            doThrow(new AdminException(AdminErrorCode.ADMIN_NOT_FOUND))
                .when(adminAuthorizationService)
                .validateAdmin(1L);

            // when & then
            assertThatThrownBy(() -> adminUserStatisticsFacade.getDailyActivity(1L, AdminDailyUserMetricsRange.DAYS_30, null, null))
                .isInstanceOf(AdminException.class)
                .extracting("errorCode")
                .isEqualTo(AdminErrorCode.ADMIN_NOT_FOUND);
            verifyNoInteractions(userService, dailyUserMetricsAggregationService);
        }
    }
}
