package kr.flint.api.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 변경 요청")
public record UpdateNicknameReq(
		@Schema(description = "변경할 닉네임 (2-10자, 영문/숫자/한글/밑줄)", example = "플린트유저")
		@NotBlank(message = "닉네임은 필수입니다.")
		@Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
		@Pattern(regexp = "^[a-zA-Z0-9가-힣_]+$", message = "닉네임은 영문, 숫자, 한글, 밑줄만 사용 가능합니다.")
		String nickname
) {
}
