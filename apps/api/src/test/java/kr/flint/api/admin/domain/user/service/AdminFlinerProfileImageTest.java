package kr.flint.api.admin.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.api.admin.common.AdminAuthorizationService;
import kr.flint.api.admin.domain.user.dto.request.AdminUserProfileImageReq;
import kr.flint.api.admin.domain.user.repository.AdminUserQueryRepository;
import kr.flint.api.admin.domain.user.repository.AdminUserQueryRepository.UserRow;
import kr.flint.api.global.storage.service.StorageFacade;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.moderation.service.UserModerationHistoryService;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import kr.flint.shared.storage.FileExtension;
import kr.flint.user.domain.User;
import kr.flint.user.domain.UserRole;
import kr.flint.user.domain.UserStatus;
import kr.flint.user.repository.UserRepository;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class AdminFlinerProfileImageTest {

    private static final String IMAGE_KEY = "user/profile/261105/4ddf949a-3d41-436c-b32d-a7584fdf574a.jpg";

    @Mock
    private AdminAuthorizationService authorizationService;
    @Mock
    private AdminUserQueryRepository queryRepository;
    @Mock
    private AdminUserModerationApplicationService moderationService;
    @Mock
    private UserModerationHistoryService historyService;
    @Mock
    private CloudFrontUrlProvider urlProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StorageFacade storageFacade;

    private AdminUserManagementFacade facade;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.createFling("플리너", "old.jpg");
        ReflectionTestUtils.setField(user, "userRole", UserRole.FLINER);
        facade = new AdminUserManagementFacade(
            authorizationService,
            queryRepository,
            moderationService,
            historyService,
            urlProvider,
            new UserService(userRepository),
            storageFacade
        );
    }

    @Test
    void replacesPhotoWithoutChangingRoleOrStatus() {
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(queryRepository.findUserRow(10L)).thenAnswer(invocation -> new UserRow(
            10L,
            user.getNickname(),
            user.getProfileImage(),
            user.getUserRole(),
            user.getStatus(),
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));
        when(historyService.getRecentHistories(10L)).thenReturn(List.of());
        when(urlProvider.resolveUrl(IMAGE_KEY)).thenReturn("https://cdn.example.com/" + IMAGE_KEY);

        var result = facade.updateProfileImage(1L, 10L, new AdminUserProfileImageReq(IMAGE_KEY));

        assertThat(user.getProfileImage()).isEqualTo(IMAGE_KEY);
        assertThat(result.profileImageUrl()).isEqualTo("https://cdn.example.com/" + IMAGE_KEY);
        assertThat(user.getUserRole()).isEqualTo(UserRole.FLINER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @ParameterizedTest
    @CsvSource({"FLING,ACTIVE", "FLINER,WITHDRAWN"})
    void rejectsNonEditableUsers(UserRole role, UserStatus status) {
        ReflectionTestUtils.setField(user, "userRole", role);
        ReflectionTestUtils.setField(user, "status", status);
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> facade.updateProfileImage(1L, 10L, new AdminUserProfileImageReq(IMAGE_KEY)))
            .isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> facade.getProfileImageUploadUrl(1L, 10L, FileExtension.JPG))
            .isInstanceOf(GeneralException.class);

        assertThat(user.getProfileImage()).isEqualTo("old.jpg");
        verifyNoInteractions(storageFacade, queryRepository);
    }

    @Test
    void rejectsUnauthorizedAdminBeforeAccessingUserOrStorage() {
        doThrow(new GeneralException(ErrorCode.FORBIDDEN)).when(authorizationService).validateAdmin(1L);

        assertThatThrownBy(() -> facade.updateProfileImage(1L, 10L, new AdminUserProfileImageReq(IMAGE_KEY)))
            .isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> facade.getProfileImageUploadUrl(1L, 10L, FileExtension.JPG))
            .isInstanceOf(GeneralException.class);

        assertThat(user.getProfileImage()).isEqualTo("old.jpg");
        verifyNoInteractions(userRepository, queryRepository, storageFacade);
    }
}
