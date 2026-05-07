package kr.flint.auth.jwt;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.RsaPublicJwk;
import kr.flint.auth.exception.AuthException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class AppleIdentityTokenVerifierTest {

	private static final String TEST_CLIENT_ID = "com.sopt.flint";
	private static final String APPLE_ISSUER = "https://appleid.apple.com";
	private static final String TEST_SUBJECT = "001234.abcdef1234567890.0000";

	private MockWebServer mockWebServer;
	private AppleIdentityTokenVerifier verifier;
	private RSAPrivateKey privateKey;
	private RSAPublicKey publicKey;
	private String kid;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() throws Exception {
		mockWebServer = new MockWebServer();
		mockWebServer.start();

		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(2048);
		KeyPair keyPair = keyGen.generateKeyPair();
		privateKey = (RSAPrivateKey) keyPair.getPrivate();
		publicKey = (RSAPublicKey) keyPair.getPublic();

		RsaPublicJwk publicJwk = Jwks.builder().key(publicKey).idFromThumbprint().build();
		kid = publicJwk.getId();

		String jwksUrl = mockWebServer.url("/auth/keys").toString();
		RestClient restClient = RestClient.builder().build();
		verifier = new AppleIdentityTokenVerifier(TEST_CLIENT_ID, jwksUrl, Duration.ofHours(24), restClient);
		objectMapper = new ObjectMapper();
	}

	@AfterEach
	void tearDown() throws IOException {
		mockWebServer.shutdown();
	}

	@Test
	@DisplayName("유효한 identityToken에서 sub 클레임을 추출한다")
	void verifyAndExtractSubject_validToken_returnsSubject() throws Exception {
		enqueueJwksResponse();
		String token = createValidIdentityToken();

		String sub = verifier.verifyAndExtractSubject(token);

		assertThat(sub).isEqualTo(TEST_SUBJECT);
	}

	@Test
	@DisplayName("만료된 identityToken은 인증 실패 예외를 던진다")
	void verifyAndExtractSubject_expiredToken_throwsAuthException() throws Exception {
		enqueueJwksResponse();
		String token = createExpiredIdentityToken();

		assertThatThrownBy(() -> verifier.verifyAndExtractSubject(token))
				.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("잘못된 issuer의 identityToken은 인증 실패 예외를 던진다")
	void verifyAndExtractSubject_wrongIssuer_throwsAuthException() throws Exception {
		enqueueJwksResponse();
		String token = Jwts.builder()
				.header().keyId(kid).and()
				.issuer("https://wrong-issuer.com")
				.audience().add(TEST_CLIENT_ID).and()
				.subject(TEST_SUBJECT)
				.issuedAt(new Date())
				.expiration(Date.from(Instant.now().plusSeconds(3600)))
				.signWith(privateKey)
				.compact();

		assertThatThrownBy(() -> verifier.verifyAndExtractSubject(token))
				.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("잘못된 audience의 identityToken은 인증 실패 예외를 던진다")
	void verifyAndExtractSubject_wrongAudience_throwsAuthException() throws Exception {
		enqueueJwksResponse();
		String token = Jwts.builder()
				.header().keyId(kid).and()
				.issuer(APPLE_ISSUER)
				.audience().add("wrong.bundle.id").and()
				.subject(TEST_SUBJECT)
				.issuedAt(new Date())
				.expiration(Date.from(Instant.now().plusSeconds(3600)))
				.signWith(privateKey)
				.compact();

		assertThatThrownBy(() -> verifier.verifyAndExtractSubject(token))
				.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("변조된 identityToken은 인증 실패 예외를 던진다")
	void verifyAndExtractSubject_tamperedToken_throwsAuthException() throws Exception {
		enqueueJwksResponse();
		String token = createValidIdentityToken();
		String tamperedToken = token.substring(0, token.lastIndexOf('.')) + ".invalidsignature";

		assertThatThrownBy(() -> verifier.verifyAndExtractSubject(tamperedToken))
				.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("빈 캐시에서 JWKS 조회 실패 시 백오프 동안 원격 조회를 반복하지 않는다")
	void verifyAndExtractSubject_jwksFailureWithEmptyCache_appliesBackoff() throws Exception {
		enqueueJwksErrorResponse();
		enqueueJwksResponse();
		String token = createValidIdentityToken();

		assertThatThrownBy(() -> verifier.verifyAndExtractSubject(token))
				.isInstanceOf(AuthException.class);
		assertThatThrownBy(() -> verifier.verifyAndExtractSubject(token))
				.isInstanceOf(AuthException.class);

		assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
	}

	private String createValidIdentityToken() {
		return Jwts.builder()
				.header().keyId(kid).and()
				.issuer(APPLE_ISSUER)
				.audience().add(TEST_CLIENT_ID).and()
				.subject(TEST_SUBJECT)
				.issuedAt(new Date())
				.expiration(Date.from(Instant.now().plusSeconds(3600)))
				.signWith(privateKey)
				.compact();
	}

	private String createExpiredIdentityToken() {
		return Jwts.builder()
				.header().keyId(kid).and()
				.issuer(APPLE_ISSUER)
				.audience().add(TEST_CLIENT_ID).and()
				.subject(TEST_SUBJECT)
				.issuedAt(Date.from(Instant.now().minusSeconds(7200)))
				.expiration(Date.from(Instant.now().minusSeconds(3600)))
				.signWith(privateKey)
				.compact();
	}

	private void enqueueJwksResponse() throws Exception {
		RsaPublicJwk publicJwk = Jwks.builder().key(publicKey).idFromThumbprint().build();
		String jwksJson = objectMapper.writeValueAsString(Map.of("keys", new Object[]{publicJwk}));

		mockWebServer.enqueue(new MockResponse()
				.setBody(jwksJson)
				.addHeader("Content-Type", "application/json"));
	}

	private void enqueueJwksErrorResponse() {
		mockWebServer.enqueue(new MockResponse()
				.setResponseCode(500)
				.setBody("apple jwks unavailable"));
	}
}
