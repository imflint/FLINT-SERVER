package kr.flint.api.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.flint.auth.enums.AuthProvider;

public record SocialVerifyReq(
        @NotNull(message = "소셜 로그인 제공자는 필수입니다.")
        AuthProvider provider,

        @NotBlank(message = "인가 코드는 필수입니다.")
        String code
) {
}
