package kr.flint.api.admin.domain.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.adminauth.domain.Admin;
import kr.flint.adminauth.service.AdminUserService;
import kr.flint.api.admin.domain.auth.dto.request.AdminLoginReq;
import kr.flint.api.admin.domain.auth.dto.request.AdminRefreshTokenReq;
import kr.flint.api.admin.domain.auth.dto.response.AdminLoginRes;
import kr.flint.auth.dto.AuthTokens;
import kr.flint.auth.dto.RefreshTokenValue;
import kr.flint.auth.enums.TokenAudience;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.service.AuthService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAuthFacade {

    private final PasswordEncoder passwordEncoder;
    private final AdminUserService adminUserService;
    private final AuthService authService;

    @Transactional
    public AdminLoginRes login(AdminLoginReq request) {
        Admin admin = adminUserService.findByUsername(request.username())
            .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));
        adminUserService.validateCanUseAdmin(admin.getId());

        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        AuthTokens tokens = authService.issueTokens(admin.getId(), null, TokenAudience.ADMIN);
        return AdminLoginRes.from(tokens, admin);
    }

    @Transactional
	public AdminLoginRes refreshTokens(AdminRefreshTokenReq request) {
		RefreshTokenValue refreshToken = authService.validateAndRotateTokenValue(
			request.refreshToken(),
			TokenAudience.ADMIN
		);
		Admin admin = adminUserService.getById(refreshToken.userId());
		if (!admin.acceptsTokenIssuedAt(refreshToken.issuedAt())) {
			throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REVOKED);
		}
		AuthTokens tokens = authService.issueTokens(admin.getId(), null, TokenAudience.ADMIN);
        return AdminLoginRes.from(tokens, admin);
    }
}
