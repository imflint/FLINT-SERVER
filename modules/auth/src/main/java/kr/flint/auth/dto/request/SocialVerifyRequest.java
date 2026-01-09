package kr.flint.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.flint.auth.domain.enums.AuthProvider;

public record SocialVerifyRequest(
        @NotNull(message = "소셜 로그인 제공자는 필수입니다.")
        AuthProvider provider,

        @NotBlank(message = "액세스 토큰은 필수입니다.")
        String accessToken
) {
}
