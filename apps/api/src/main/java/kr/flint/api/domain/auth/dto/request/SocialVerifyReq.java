package kr.flint.api.domain.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import kr.flint.auth.enums.AuthProvider;
import org.springframework.util.StringUtils;

public record SocialVerifyReq(
        @NotNull(message = "소셜 로그인 제공자는 필수입니다.")
        AuthProvider provider,

        String code,

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
