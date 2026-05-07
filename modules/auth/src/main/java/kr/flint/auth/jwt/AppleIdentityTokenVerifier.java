package kr.flint.auth.jwt;

import java.security.Key;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.RsaPublicJwk;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppleIdentityTokenVerifier {

	private static final String APPLE_ISSUER = "https://appleid.apple.com";
	private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(5);
	private static final Duration REFRESH_FAILURE_BACKOFF = Duration.ofSeconds(30);

	private final String clientId;
	private final String jwksUrl;
	private final Duration jwksCacheTtl;
	private final RestClient restClient;

	private volatile Map<String, RSAPublicKey> cachedKeys = Map.of();
	private volatile Instant cacheExpiry = Instant.MIN;
	private volatile Instant lastRefreshTime = Instant.MIN;
	private volatile Instant lastRefreshFailureTime = Instant.MIN;

	public AppleIdentityTokenVerifier(String clientId, String jwksUrl, Duration jwksCacheTtl, RestClient restClient) {
		this.clientId = clientId;
		this.jwksUrl = jwksUrl;
		this.jwksCacheTtl = jwksCacheTtl;
		this.restClient = restClient;
	}

	// Apple identityToken(JWT) 검증 후 사용자 식별자(sub) 추출
	public String verifyAndExtractSubject(String identityToken) {
		try {
			Claims claims = Jwts.parser()
					.keyLocator(new AppleKeyLocator())
					.requireIssuer(APPLE_ISSUER)
					.requireAudience(clientId)
					.build()
					.parseSignedClaims(identityToken)
					.getPayload();

			String sub = claims.getSubject();
			if (sub == null || sub.isBlank()) {
				throw new AuthException(AuthErrorCode.SOCIAL_AUTH_FAILED);
			}

			return sub;
		} catch (AuthException e) {
			throw e;
		} catch (JwtException e) {
			log.warn("Apple identityToken 검증 실패: {}", e.getMessage());
			throw new AuthException(AuthErrorCode.SOCIAL_AUTH_FAILED);
		}
	}

	private Map<String, RSAPublicKey> getApplePublicKeys() {
		Instant now = Instant.now();
		if (isCacheFresh(now)) {
			return cachedKeys;
		}
		return refreshApplePublicKeys();
	}

	private synchronized Map<String, RSAPublicKey> refreshApplePublicKeys() {
		Instant now = Instant.now();
		if (isCacheFresh(now)) {
			return cachedKeys;
		}

		if (isInFailureBackoff(now)) {
			log.warn("Apple JWKS 갱신 실패 백오프 적용 중: cachedKeyCount={}", cachedKeys.size());
			return cachedKeysOrThrow();
		}

		if (now.isBefore(lastRefreshTime.plus(MIN_REFRESH_INTERVAL)) && !cachedKeys.isEmpty()) {
			return cachedKeys;
		}

		try {
			String jwksJson = restClient.get()
					.uri(jwksUrl)
					.retrieve()
					.body(String.class);

			JwkSet jwkSet = Jwks.setParser().build().parse(jwksJson);

			Map<String, RSAPublicKey> keys = new ConcurrentHashMap<>();
			jwkSet.forEach(jwk -> {
				if (jwk instanceof RsaPublicJwk rsaPublicJwk) {
					keys.put(jwk.getId(), rsaPublicJwk.toKey());
				}
			});

			if (keys.isEmpty()) {
				throw new IllegalStateException("Apple JWKS에 RSA 공개키가 없습니다.");
			}

			Instant refreshedAt = Instant.now();
			cachedKeys = keys;
			cacheExpiry = refreshedAt.plus(jwksCacheTtl);
			lastRefreshTime = refreshedAt;
			lastRefreshFailureTime = Instant.MIN;

			log.info("Apple JWKS 갱신 완료: {}개 키 캐시됨", keys.size());
			return keys;
		} catch (RestClientException | JwtException | IllegalStateException e) {
			lastRefreshFailureTime = Instant.now();
			log.error("Apple JWKS 조회 실패: {}", e.getMessage());
			return cachedKeysOrThrow();
		}
	}

	private boolean isCacheFresh(Instant now) {
		return now.isBefore(cacheExpiry) && !cachedKeys.isEmpty();
	}

	private boolean isInFailureBackoff(Instant now) {
		return now.isBefore(lastRefreshFailureTime.plus(REFRESH_FAILURE_BACKOFF));
	}

	private Map<String, RSAPublicKey> cachedKeysOrThrow() {
		if (!cachedKeys.isEmpty()) {
			return cachedKeys;
		}
		throw new AuthException(AuthErrorCode.SOCIAL_AUTH_SERVER_ERROR);
	}

	private class AppleKeyLocator extends LocatorAdapter<Key> {
		@Override
		protected Key locate(JwsHeader header) {
			String kid = header.getKeyId();
			if (kid == null) {
				throw new AuthException(AuthErrorCode.SOCIAL_AUTH_FAILED);
			}

			Map<String, RSAPublicKey> keys = getApplePublicKeys();
			RSAPublicKey key = keys.get(kid);

			if (key == null) {
				keys = refreshApplePublicKeys();
				key = keys.get(kid);
			}

			if (key == null) {
				log.warn("Apple JWKS에서 kid={} 에 해당하는 키를 찾을 수 없음", kid);
				throw new AuthException(AuthErrorCode.SOCIAL_AUTH_FAILED);
			}

			return key;
		}
	}
}
