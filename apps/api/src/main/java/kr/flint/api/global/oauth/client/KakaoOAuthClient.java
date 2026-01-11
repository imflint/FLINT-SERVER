package kr.flint.api.global.oauth.client;

import kr.flint.api.global.oauth.dto.KakaoTokenRes;
import kr.flint.api.global.oauth.dto.KakaoUserRes;
import kr.flint.api.global.oauth.properties.KakaoProperties;
import kr.flint.auth.dto.SocialUserInfo;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private final RestClient kakaoRestClient;
    private final KakaoProperties kakaoProperties;

    public SocialUserInfo getUserInfoByCode(String authorizationCode) {
        KakaoTokenRes tokenResponse = getToken(authorizationCode);
        return getUserInfo(tokenResponse.accessToken());
    }

    private KakaoTokenRes getToken(String authorizationCode) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", kakaoProperties.clientId());
            params.add("client_secret", kakaoProperties.clientSecret());
            params.add("redirect_uri", kakaoProperties.redirectUri());
            params.add("code", authorizationCode);

            KakaoTokenRes response = kakaoRestClient.post()
                    .uri(kakaoProperties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("카카오 토큰 발급 에러: {}", res.getStatusCode());
                        throw new AuthException(AuthErrorCode.SOCIAL_AUTH_INVALID_CODE);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("카카오 서버 에러: {}", res.getStatusCode());
                        throw new AuthException(AuthErrorCode.SOCIAL_AUTH_SERVER_ERROR);
                    })
                    .body(KakaoTokenRes.class);

            if (response == null || response.accessToken() == null) {
                throw new AuthException(AuthErrorCode.SOCIAL_AUTH_FAILED);
            }

            return response;
        } catch (RestClientException e) {
            log.error("카카오 토큰 발급 실패: {}", e.getMessage());
            throw new AuthException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    private SocialUserInfo getUserInfo(String accessToken) {
        try {
            KakaoUserRes response = kakaoRestClient.get()
                    .uri(kakaoProperties.userInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("카카오 사용자 정보 조회 4xx 에러: {}", res.getStatusCode());
                        throw new AuthException(AuthErrorCode.SOCIAL_AUTH_FAILED);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("카카오 서버 에러: {}", res.getStatusCode());
                        throw new AuthException(AuthErrorCode.SOCIAL_AUTH_SERVER_ERROR);
                    })
                    .body(KakaoUserRes.class);

            if (response == null || response.id() == null) {
                throw new AuthException(AuthErrorCode.SOCIAL_AUTH_FAILED);
            }

            return SocialUserInfo.of(String.valueOf(response.id()));
        } catch (RestClientException e) {
            log.error("카카오 사용자 정보 조회 실패: {}", e.getMessage());
            throw new AuthException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }
}
