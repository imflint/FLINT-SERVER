package kr.flint.admin.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.adminauth.domain.Admin;
import kr.flint.adminauth.service.AdminUserService;
import kr.flint.admin.domain.auth.dto.request.AdminLoginReq;
import kr.flint.admin.domain.auth.dto.request.AdminRefreshTokenReq;
import kr.flint.admin.domain.auth.dto.response.AdminLoginRes;
import kr.flint.auth.dto.AuthTokens;
import kr.flint.auth.enums.TokenAudience;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AdminAuthFacadeTest {

    private static final Long ADMIN_ID = 10L;
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private AuthService authService;

    private PasswordEncoder passwordEncoder;
    private AdminAuthFacade adminAuthFacade;
    private Admin admin;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        admin = Admin.create(USERNAME, passwordEncoder.encode(PASSWORD), LocalDateTime.now());
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        adminAuthFacade = new AdminAuthFacade(passwordEncoder, adminUserService, authService);
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        void adminSuccess() {
            when(adminUserService.findByUsername(USERNAME)).thenReturn(java.util.Optional.of(admin));
            when(authService.issueTokens(ADMIN_ID, null, TokenAudience.ADMIN))
                .thenReturn(AuthTokens.of("access", "refresh", ADMIN_ID));

            AdminLoginRes result = adminAuthFacade.login(new AdminLoginReq(USERNAME, PASSWORD));

            assertThat(result.accessToken()).isEqualTo("access");
            assertThat(result.refreshToken()).isEqualTo("refresh");
            assertThat(result.adminId()).isEqualTo(ADMIN_ID);
        }

        @Test
        void invalidPassword() {
            when(adminUserService.findByUsername(USERNAME)).thenReturn(java.util.Optional.of(admin));

            assertThatThrownBy(() -> adminAuthFacade.login(new AdminLoginReq(USERNAME, "wrong")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

            verify(authService, never()).issueTokens(ADMIN_ID, null, TokenAudience.ADMIN);
        }

        @Test
        void invalidUsername() {
            when(adminUserService.findByUsername("other")).thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> adminAuthFacade.login(new AdminLoginReq("other", PASSWORD)))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

            verify(authService, never()).issueTokens(ADMIN_ID, null, TokenAudience.ADMIN);
        }
    }

    @Nested
    @DisplayName("refreshTokens")
    class RefreshTokens {

        @Test
        void adminSuccess() {
            when(authService.validateAndRotateToken("old-refresh", TokenAudience.ADMIN)).thenReturn(ADMIN_ID);
            when(adminUserService.getById(ADMIN_ID)).thenReturn(admin);
            when(authService.issueTokens(ADMIN_ID, null, TokenAudience.ADMIN))
                .thenReturn(AuthTokens.of("new-access", "new-refresh", ADMIN_ID));

            AdminLoginRes result = adminAuthFacade.refreshTokens(new AdminRefreshTokenReq("old-refresh"));

            assertThat(result.accessToken()).isEqualTo("new-access");
            assertThat(result.refreshToken()).isEqualTo("new-refresh");
            assertThat(result.adminId()).isEqualTo(ADMIN_ID);
        }
    }
}
