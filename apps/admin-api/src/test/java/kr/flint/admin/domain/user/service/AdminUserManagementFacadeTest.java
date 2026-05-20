package kr.flint.admin.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.user.dto.request.AdminUserModerationReq;
import kr.flint.admin.domain.user.dto.response.AdminUserDetailRes;
import kr.flint.admin.domain.user.repository.AdminUserQueryRepository;
import kr.flint.admin.domain.user.repository.AdminUserQueryRepository.UserRow;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.moderation.domain.UserModerationHistory;
import kr.flint.moderation.service.UserModerationHistoryService;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import kr.flint.user.domain.UserRole;
import kr.flint.user.domain.UserStatus;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementFacadeTest {

    @Mock
    private AdminAuthorizationService adminAuthorizationService;

    @Mock
    private AdminUserQueryRepository adminUserQueryRepository;

    @Mock
    private AdminUserModerationApplicationService moderationApplicationService;

    @Mock
    private UserModerationHistoryService userModerationHistoryService;

    @Mock
    private CloudFrontUrlProvider cloudFrontUrlProvider;

    @InjectMocks
    private AdminUserManagementFacade adminUserManagementFacade;

    private UserRow userRow;

    @BeforeEach
    void setUp() {
        userRow = new UserRow(
            10L,
            "플린트",
            "profile.jpg",
            UserRole.FLINER,
            UserStatus.ACTIVE,
            2,
            LocalDateTime.of(2026, 5, 1, 10, 0),
            LocalDateTime.of(2026, 5, 31, 23, 59),
            null,
            null,
            null,
            LocalDateTime.of(2026, 5, 1, 9, 0),
            LocalDateTime.of(2026, 5, 2, 9, 0)
        );
    }

    @Test
    @DisplayName("회원 목록을 page/size 기반으로 조회")
    void getUsers() {
        LocalDate createdFrom = LocalDate.of(2026, 5, 1);
        LocalDate createdTo = LocalDate.of(2026, 5, 20);
        when(adminUserQueryRepository.findUserIds(
            "플린트",
            UserStatus.ACTIVE,
            createdFrom.atStartOfDay(),
            createdTo.plusDays(1).atStartOfDay(),
            1,
            20
        )).thenReturn(List.of(10L));
        when(adminUserQueryRepository.countUsers(
            "플린트",
            UserStatus.ACTIVE,
            createdFrom.atStartOfDay(),
            createdTo.plusDays(1).atStartOfDay()
        )).thenReturn(1L);
        when(adminUserQueryRepository.findUserRows(List.of(10L))).thenReturn(List.of(userRow));
        when(cloudFrontUrlProvider.resolveUrl("profile.jpg")).thenReturn("https://cdn.flint/profile.jpg");

        var result = adminUserManagementFacade.getUsers(1L, "플린트", UserStatus.ACTIVE, createdFrom, createdTo, 1, 20);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).nickname()).isEqualTo("플린트");
        assertThat(result.data().get(0).profileImageUrl()).isEqualTo("https://cdn.flint/profile.jpg");
        assertThat(result.meta().page()).isEqualTo(1);
        assertThat(result.meta().totalElements()).isEqualTo(1L);
        verify(adminAuthorizationService).validateAdmin(1L);
    }

    @Test
    @DisplayName("회원 상세와 최근 제재 이력을 조회")
    void getUser() {
        UserModerationHistory history = UserModerationHistory.create(
            10L,
            1L,
            UserModerationAction.WARN,
            null,
            "메모"
        );
        when(adminUserQueryRepository.findUserRow(10L)).thenReturn(userRow);
        when(userModerationHistoryService.getRecentHistories(10L)).thenReturn(List.of(history));
        when(cloudFrontUrlProvider.resolveUrl("profile.jpg")).thenReturn("https://cdn.flint/profile.jpg");

        AdminUserDetailRes result = adminUserManagementFacade.getUser(1L, 10L);

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.uploadRestricted()).isTrue();
        assertThat(result.recentModerations()).hasSize(1);
    }

    @Test
    @DisplayName("회원 제재를 적용하고 이력을 저장")
    void moderateUser() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 31, 23, 59);
        when(adminUserQueryRepository.findUserRow(10L)).thenReturn(userRow);
        when(userModerationHistoryService.getRecentHistories(10L)).thenReturn(List.of());
        when(cloudFrontUrlProvider.resolveUrl("profile.jpg")).thenReturn("https://cdn.flint/profile.jpg");

        AdminUserDetailRes result = adminUserManagementFacade.moderateUser(
            1L,
            10L,
            new AdminUserModerationReq(UserModerationAction.SUSPEND, expiresAt, " 운영 메모 ")
        );

        assertThat(result.userId()).isEqualTo(10L);
        verify(moderationApplicationService).apply(10L, UserModerationAction.SUSPEND, expiresAt);
        verify(userModerationHistoryService).record(10L, 1L, UserModerationAction.SUSPEND, expiresAt, "운영 메모");
    }

    @Test
    @DisplayName("업로드 제한과 이용 정지는 종료 시각이 필수")
    void validateExpiresAt() {
        assertThatThrownBy(() -> adminUserManagementFacade.moderateUser(
            1L,
            10L,
            new AdminUserModerationReq(UserModerationAction.RESTRICT_UPLOAD, null, null)
        ))
            .isInstanceOf(GeneralException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(moderationApplicationService, never()).apply(any(), any(), any());
        verify(userModerationHistoryService, never()).record(any(), any(), any(), any(), any());
    }
}
