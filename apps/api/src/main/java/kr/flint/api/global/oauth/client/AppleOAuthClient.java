package kr.flint.api.global.oauth.client;

import org.springframework.stereotype.Component;

import kr.flint.auth.dto.SocialUserInfo;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.jwt.AppleIdentityTokenVerifier;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppleOAuthClient {

	private final AppleIdentityTokenVerifier appleIdentityTokenVerifier;

	// Apple identityToken(JWT) 검증 후 사용자 식별자 추출 - Mobile
	public SocialUserInfo getUserInfoByIdentityToken(String identityToken) {
		String sub = appleIdentityTokenVerifier.verifyAndExtractSubject(identityToken);
		return SocialUserInfo.of(sub);
	}

	// Web 플로우 미구현
	public SocialUserInfo getUserInfoByCode(String authorizationCode) {
		throw new AuthException(AuthErrorCode.UNSUPPORTED_SOCIAL_FLOW);
	}
}
