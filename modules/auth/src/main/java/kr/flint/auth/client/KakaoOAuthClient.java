package kr.flint.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.flint.auth.config.KakaoProperties;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.shared.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private final RestClient kakaoRestClient;
    private final KakaoProperties kakaoProperties;

    // 카카오 사용자 정보 조회
    public KakaoUserInfo getUserInfo(String accessToken) {
        try {
            KakaoUserResponse response = kakaoRestClient.get()
                    .uri(kakaoProperties.userInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .retrieve()
                    .body(KakaoUserResponse.class);

            if (response == null) {
                throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_FAILED);
            }

            return KakaoUserInfo.from(response);
        } catch (RestClientException e) {
            log.error("카카오 API 호출 실패: {}", e.getMessage());
            throw new GeneralException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    // 카카오 API 응답 DTO
    public record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
        public record KakaoAccount(
                String email,
                KakaoProfile profile
        ) {
            public record KakaoProfile(
                    String nickname
            ) {}
        }
    }

    // 내부에서 사용하는 사용자 정보 DTO
    public record KakaoUserInfo(
            String providerUserId,
            String email,
            String nickname
    ) {
        public static KakaoUserInfo from(KakaoUserResponse response) {
            String email = null;
            String nickname = null;

            if (response.kakaoAccount() != null) {
                email = response.kakaoAccount().email();
                if (response.kakaoAccount().profile() != null) {
                    nickname = response.kakaoAccount().profile().nickname();
                }
            }

            return new KakaoUserInfo(
                    String.valueOf(response.id()),
                    email,
                    nickname
            );
        }
    }
}
