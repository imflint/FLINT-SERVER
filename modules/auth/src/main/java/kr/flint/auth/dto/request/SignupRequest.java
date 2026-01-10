package kr.flint.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SignupRequest(
        @NotBlank(message = "임시 토큰은 필수입니다.")
        String tempToken,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
        String nickname,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String realName,

        List<Long> favoriteContentIds,

        List<Long> subscribedOttIds
) {
}
