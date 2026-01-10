package kr.flint.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SignupRequest(
        @NotBlank(message = "임시 토큰은 필수입니다.")
        String tempToken,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임은 영문, 숫자, 한글만 사용 가능합니다.")
        String nickname,

        List<Long> favoriteContentIds,

        List<Long> subscribedOttIds
) {
}
