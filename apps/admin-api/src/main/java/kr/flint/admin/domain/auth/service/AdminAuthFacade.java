package kr.flint.admin.domain.auth.service;

import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.admin.domain.auth.dto.request.AdminLoginReq;
import kr.flint.admin.domain.auth.dto.response.AdminLoginRes;
import kr.flint.admin.domain.auth.properties.AdminAuthProperties;
import kr.flint.auth.dto.AuthTokens;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.service.AuthService;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import kr.flint.user.domain.UserRole;
import kr.flint.user.dto.response.UserAuthInfo;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAuthFacade {

	private final AdminAuthProperties adminAuthProperties;
	private final PasswordEncoder passwordEncoder;
	private final UserService userService;
	private final AuthService authService;

	@Transactional
	public AdminLoginRes login(AdminLoginReq request) {
		if (!adminAuthProperties.configured()) {
			throw new GeneralException(ErrorCode.INTERNAL_SERVER_ERROR);
		}

		if (!matchesCredentials(request)) {
			throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
		}

		UserAuthInfo authInfo = userService.getAuthInfo(adminAuthProperties.userId());
		validateAdmin(authInfo);

		AuthTokens tokens = authService.issueTokens(authInfo.userId(), authInfo.role());
		return AdminLoginRes.from(tokens, authInfo.nickname());
	}

	private boolean matchesCredentials(AdminLoginReq request) {
		return Objects.equals(adminAuthProperties.username(), request.username())
			&& passwordEncoder.matches(request.password(), adminAuthProperties.passwordHash());
	}

	private void validateAdmin(UserAuthInfo authInfo) {
		if (!UserRole.ADMIN.name().equals(authInfo.role())) {
			throw new GeneralException(ErrorCode.FORBIDDEN);
		}
	}
}
