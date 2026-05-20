package kr.flint.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.moderation.domain.UserModerationHistory;
import kr.flint.moderation.repository.UserModerationHistoryRepository;

@ExtendWith(MockitoExtension.class)
class UserModerationHistoryServiceTest {

    @Mock
    private UserModerationHistoryRepository userModerationHistoryRepository;

    @Captor
    private ArgumentCaptor<UserModerationHistory> historyCaptor;

    @InjectMocks
    private UserModerationHistoryService userModerationHistoryService;

    @Test
    @DisplayName("회원 제재 이력을 저장")
    void recordHistory() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 31, 23, 59);

        userModerationHistoryService.record(10L, 1L, UserModerationAction.SUSPEND, expiresAt, "운영 메모");

        verify(userModerationHistoryRepository).save(historyCaptor.capture());
        UserModerationHistory history = historyCaptor.getValue();
        assertThat(history.getUserId()).isEqualTo(10L);
        assertThat(history.getAdminUserId()).isEqualTo(1L);
        assertThat(history.getAction()).isEqualTo(UserModerationAction.SUSPEND);
        assertThat(history.getActionExpiresAt()).isEqualTo(expiresAt);
        assertThat(history.getAdminMemo()).isEqualTo("운영 메모");
    }

    @Test
    @DisplayName("최근 회원 제재 이력을 조회")
    void getRecentHistories() {
        UserModerationHistory history = UserModerationHistory.create(
            10L,
            1L,
            UserModerationAction.WARN,
            null,
            null
        );
        when(userModerationHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(history));

        List<UserModerationHistory> result = userModerationHistoryService.getRecentHistories(10L);

        assertThat(result).containsExactly(history);
    }
}
