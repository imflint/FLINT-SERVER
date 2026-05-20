package kr.flint.admin.domain.user.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class AdminUserModerationApplicationServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminUserModerationApplicationService applicationService;

    @Test
    @DisplayName("경고 조치를 적용")
    void applyWarn() {
        applicationService.apply(10L, UserModerationAction.WARN, null);

        verify(userService).warn(10L);
    }

    @Test
    @DisplayName("업로드 제한 조치를 적용")
    void applyRestrictUpload() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 31, 23, 59);

        applicationService.apply(10L, UserModerationAction.RESTRICT_UPLOAD, expiresAt);

        verify(userService).restrictUpload(10L, expiresAt);
    }

    @Test
    @DisplayName("이용 정지 조치를 적용")
    void applySuspend() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 31, 23, 59);

        applicationService.apply(10L, UserModerationAction.SUSPEND, expiresAt);

        verify(userService).suspend(10L, expiresAt);
    }

    @Test
    @DisplayName("유지 조치는 회원 상태를 변경하지 않음")
    void applyKeep() {
        applicationService.apply(10L, UserModerationAction.KEEP, null);

        verify(userService, never()).warn(10L);
        verify(userService, never()).restrictUpload(10L, null);
        verify(userService, never()).suspend(10L, null);
    }
}
