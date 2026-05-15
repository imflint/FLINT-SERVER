package kr.flint.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.user.domain.User;
import kr.flint.user.dto.response.UserAuthInfo;
import kr.flint.user.exception.UserErrorCode;
import kr.flint.user.exception.UserException;
import kr.flint.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("회원가입 시 프로필 이미지를 저장")
        void saveProfileImage() {
            // given
            when(userRepository.existsByNickname("플린트")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0, User.class);
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });

            // when
            UserAuthInfo result = userService.create("플린트", "user/profile/profile-key.jpg");

            // then
            assertThat(result.userId()).isEqualTo(1L);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getProfileImage()).isEqualTo("user/profile/profile-key.jpg");
        }

        @Test
        @DisplayName("닉네임은 2자 이상 8자 이하만 허용")
        void validateNicknameLength() {
            // when & then
            assertThatThrownBy(() -> userService.create("가", null))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_NICKNAME);
            assertThatThrownBy(() -> userService.create("123456789", null))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_NICKNAME);
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("닉네임은 한글, 영문, 숫자만 허용")
        void validateNicknameCharacters() {
            // when & then
            assertThatThrownBy(() -> userService.create("플린트_", null))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_NICKNAME);
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("moderation")
    class Moderation {

        @Test
        @DisplayName("경고 조치는 사용자 경고 횟수를 증가")
        void warn() {
            User user = User.createFling("플린트");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.warn(1L);

            assertThat(user.getWarningCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("업로드 제한 중인 사용자는 업로드할 수 없음")
        void validateUploadRestricted() {
            User user = User.createFling("플린트");
            user.restrictUpload(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.validateCanUpload(1L))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_UPLOAD_RESTRICTED);
        }

        @Test
        @DisplayName("미래에 시작되는 업로드 제한은 아직 업로드를 막지 않음")
        void futureUploadRestrictionDoesNotBlockUpload() {
            User user = User.createFling("플린트");
            user.restrictUpload(LocalDateTime.now().plusDays(1), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatCode(() -> userService.validateCanUpload(1L)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("미래에 시작되는 정지는 아직 서비스 이용을 막지 않음")
        void futureSuspensionDoesNotBlockService() {
            User user = User.createFling("플린트");
            user.suspend(LocalDateTime.now().plusDays(1), null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThat(userService.canUseService(1L)).isTrue();
        }

        @Test
        @DisplayName("활성 사용자 수는 Repository 조회 결과를 반환")
        void countActiveUsers() {
            when(userRepository.countActiveUsers(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(12L);

            assertThat(userService.countActiveUsers()).isEqualTo(12L);
        }
    }
}
