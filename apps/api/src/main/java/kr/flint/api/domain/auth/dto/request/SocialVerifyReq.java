package kr.flint.api.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import kr.flint.auth.enums.AuthProvider;
import org.springframework.util.StringUtils;

@Schema(description = "소셜 로그인 인증 요청")
public record SocialVerifyReq(
        @Schema(description = "소셜 로그인 제공자 (KAKAO, APPLE)", example = "KAKAO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "소셜 로그인 제공자는 필수입니다.")
        AuthProvider provider,

        @Schema(description = "인가 코드 (Kakao Web 로그인 시 사용)", example = "authorization_code_from_oauth")
        String code,

        @Schema(description = "Kakao accessToken 또는 Apple identityToken (Mobile 로그인 시 사용)", example = "access_token_from_sdk")
        String accessToken
) {
        @AssertTrue(message = "code 또는 accessToken 중 하나는 필수입니다.")
        private boolean isCodeOrAccessTokenPresent() {
                return StringUtils.hasText(code) || StringUtils.hasText(accessToken);
        }

        public boolean hasAccessToken() {
                return StringUtils.hasText(accessToken);
        }
}
