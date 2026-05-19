package kr.flint.user.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.user.repository.DailyVisitorActivityRepository;

@ExtendWith(MockitoExtension.class)
class DailyVisitorActivityServiceTest {

    @Mock
    private DailyVisitorActivityRepository dailyVisitorActivityRepository;

    @InjectMocks
    private DailyVisitorActivityService dailyVisitorActivityService;

    @Nested
    @DisplayName("recordVisit")
    class RecordVisit {

        @Test
        @DisplayName("방문 기록은 insert ignore로 첫 방문만 저장")
        void insertFirstVisitOnly() {
            // given
            LocalDate date = LocalDate.of(2026, 5, 18);
            LocalDateTime seenAt = LocalDateTime.of(2026, 5, 18, 10, 0);

            // when
            dailyVisitorActivityService.recordVisit("visitor-key", 1L, date, seenAt);

            // then
            verify(dailyVisitorActivityRepository).insertFirstVisit(
                anyLong(),
                eq("visitor-key"),
                eq(1L),
                eq(date),
                eq(seenAt)
            );
        }

        @Test
        @DisplayName("중복 방문은 Repository insert ignore 결과와 무관하게 실패하지 않음")
        void ignoreDuplicateVisit() {
            // given
            LocalDate date = LocalDate.of(2026, 5, 18);
            LocalDateTime seenAt = LocalDateTime.of(2026, 5, 18, 10, 0);
            when(dailyVisitorActivityRepository.insertFirstVisit(
                anyLong(),
                eq("visitor-key"),
                isNull(),
                eq(date),
                eq(seenAt)
            )).thenReturn(0);

            // when
            dailyVisitorActivityService.recordVisit("visitor-key", null, date, seenAt);

            // then
            verify(dailyVisitorActivityRepository).insertFirstVisit(
                anyLong(),
                eq("visitor-key"),
                isNull(),
                eq(date),
                eq(seenAt)
            );
        }

        @Test
        @DisplayName("방문자 키가 비어 있으면 기록하지 않음")
        void skipBlankVisitorKey() {
            // when
            dailyVisitorActivityService.recordVisit(" ", null, LocalDate.of(2026, 5, 18), LocalDateTime.now());

            // then
            verifyNoInteractions(dailyVisitorActivityRepository);
        }
    }

}
