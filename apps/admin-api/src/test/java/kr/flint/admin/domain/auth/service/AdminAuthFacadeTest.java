package kr.flint.admin.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import kr.flint.admin.domain.auth.dto.request.AdminLoginReq;
import kr.flint.admin.domain.auth.dto.response.AdminLoginRes;
import kr.flint.admin.domain.auth.properties.AdminAuthProperties;
import kr.flint.auth.dto.AuthTokens;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.service.AuthService;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import kr.flint.user.dto.response.UserAuthInfo;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class AdminAuthFacadeTest {

	private static final Long ADMIN_USER_ID = 10L;
	private static final String USERNAME = "admin";
	private static final String PASSWORD = "password";

	@Mock
	private UserService userService;

	@Mock
	private AuthService authService;

	private PasswordEncoder passwordEncoder;
	private AdminAuthFacade adminAuthFacade;

	@BeforeEach
	void setUp() {
		passwordEncoder = new BCryptPasswordEncoder();
		AdminAuthProperties properties = new AdminAuthProperties(
			ADMIN_USER_ID,
			USERNAME,
			passwordEncoder.encode(PASSWORD)
		);
		adminAuthFacade = new AdminAuthFacade(properties, passwordEncoder, userService, authService);
	}

	@Nested
	@DisplayName("login")
	class Login {

		@Test
		void adminSuccess() {
			when(userService.getAuthInfo(ADMIN_USER_ID)).thenReturn(UserAuthInfo.of(ADMIN_USER_ID, "admin", "ADMIN"));
			when(authService.issueTokens(ADMIN_USER_ID, "ADMIN")).thenReturn(AuthTokens.of("access", "refresh", ADMIN_USER_ID));

			AdminLoginRes result = adminAuthFacade.login(new AdminLoginReq(USERNAME, PASSWORD));

			assertThat(result.accessToken()).isEqualTo("access");
			assertThat(result.refreshToken()).isEqualTo("refresh");
			assertThat(result.userId()).isEqualTo(ADMIN_USER_ID);
			assertThat(result.nickname()).isEqualTo("admin");
		}

		@Test
		void invalidPassword() {
			assertThatThrownBy(() -> adminAuthFacade.login(new AdminLoginReq(USERNAME, "wrong")))
				.isInstanceOf(AuthException.class)
				.extracting("errorCode")
				.isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

			verify(authService, never()).issueTokens(ADMIN_USER_ID, "ADMIN");
		}

		@Test
		void nonAdminUser() {
			when(userService.getAuthInfo(ADMIN_USER_ID)).thenReturn(UserAuthInfo.of(ADMIN_USER_ID, "fling", "FLING"));

			assertThatThrownBy(() -> adminAuthFacade.login(new AdminLoginReq(USERNAME, PASSWORD)))
				.isInstanceOf(GeneralException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.FORBIDDEN);

			verify(authService, never()).issueTokens(ADMIN_USER_ID, "ADMIN");
		}

		@Test
		void invalidUsername() {
			assertThatThrownBy(() -> adminAuthFacade.login(new AdminLoginReq("other", PASSWORD)))
				.isInstanceOf(AuthException.class)
				.extracting("errorCode")
				.isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

			verify(userService, never()).getAuthInfo(ADMIN_USER_ID);
			verify(authService, never()).issueTokens(ADMIN_USER_ID, "ADMIN");
		}
	}
}
