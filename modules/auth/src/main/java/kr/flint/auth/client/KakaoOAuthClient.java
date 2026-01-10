package kr.flint.auth.client;

import kr.flint.auth.client.dto.KakaoTokenResponse;
import kr.flint.auth.client.dto.KakaoUserInfo;
import kr.flint.auth.client.dto.KakaoUserResponse;
import kr.flint.auth.config.KakaoProperties;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.shared.exception.GeneralException;
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

    // Authorization Code로 Access Token 발급
    public KakaoTokenResponse getToken(String authorizationCode) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", kakaoProperties.clientId());
            params.add("client_secret", kakaoProperties.clientSecret());
            params.add("redirect_uri", kakaoProperties.redirectUri());
            params.add("code", authorizationCode);

            KakaoTokenResponse response = kakaoRestClient.post()
                    .uri(kakaoProperties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("카카오 토큰 발급 4xx 에러: {}", res.getStatusCode());
                        throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_INVALID_CODE);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("카카오 서버 에러: {}", res.getStatusCode());
                        throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_SERVER_ERROR);
                    })
                    .body(KakaoTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_FAILED);
            }

            return response;
        } catch (RestClientException e) {
            log.error("카카오 토큰 발급 실패: {}", e.getMessage());
            throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    // Access Token으로 사용자 정보 조회
    public KakaoUserInfo getUserInfo(String accessToken) {
        try {
            KakaoUserResponse response = kakaoRestClient.get()
                    .uri(kakaoProperties.userInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.warn("카카오 사용자 정보 조회 4xx 에러: {}", res.getStatusCode());
                        throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_FAILED);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("카카오 서버 에러: {}", res.getStatusCode());
                        throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_SERVER_ERROR);
                    })
                    .body(KakaoUserResponse.class);

            if (response == null) {
                throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_FAILED);
            }

            return KakaoUserInfo.from(response);
        } catch (RestClientException e) {
            log.error("카카오 사용자 정보 조회 실패: {}", e.getMessage());
            throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    // Authorization Code로 사용자 정보 조회 (토큰 발급 + 사용자 정보 조회)
    public KakaoUserInfo getUserInfoByCode(String authorizationCode) {
        KakaoTokenResponse tokenResponse = getToken(authorizationCode);
        return getUserInfo(tokenResponse.accessToken());
    }
}
