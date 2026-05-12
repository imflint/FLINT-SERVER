package kr.flint.api.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "회원가입 요청")
public record SignupReq(
        @Schema(description = "소셜 인증 후 발급받은 임시 토큰", example = "temp_token_xxx", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "임시 토큰은 필수입니다.")
        String tempToken,

        @Schema(description = "닉네임 (2-10자, 영문/숫자/한글만 가능)", example = "플린트", minLength = 2, maxLength = 10, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임은 영문, 숫자, 한글만 사용 가능합니다.")
        String nickname,

        @Schema(description = "선호 콘텐츠 ID 목록 (온보딩에서 선택)", example = "[\"1\", \"2\", \"3\"]")
        List<@NotBlank(message = "선호 콘텐츠 ID는 비어 있을 수 없습니다.")
        @Pattern(regexp = "^\\d+$", message = "선호 콘텐츠 ID는 숫자 문자열이어야 합니다.") String> favoriteContentIds,

        @Schema(description = "동의한 약관 ID 목록", example = "[\"1\", \"2\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "동의한 약관 ID 목록은 필수입니다.")
        List<@NotNull(message = "약관 ID는 null일 수 없습니다.")
        @Pattern(regexp = "^\\d+$", message = "약관 ID는 숫자 문자열이어야 합니다.") String> agreedTermsIds
) {
    public List<Long> favoriteContentIdValues() {
        return toLongIds(favoriteContentIds);
    }

    public List<Long> agreedTermsIdValues() {
        return toLongIds(agreedTermsIds);
    }

    private static List<Long> toLongIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .map(Long::valueOf)
                .toList();
    }
}
