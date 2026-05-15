package kr.flint.api.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.flint.user.domain.NicknamePolicy;

@Schema(description = "닉네임 변경 요청")
public record UpdateNicknameReq(
		@Schema(description = "변경할 닉네임 (2-8자, 한글/영문/숫자 혼용 가능)", example = "플린트유저", minLength = NicknamePolicy.MIN_LENGTH, maxLength = NicknamePolicy.MAX_LENGTH)
		@NotBlank(message = NicknamePolicy.MESSAGE)
		@Size(min = NicknamePolicy.MIN_LENGTH, max = NicknamePolicy.MAX_LENGTH, message = NicknamePolicy.MESSAGE)
		@Pattern(regexp = NicknamePolicy.REGEX, message = NicknamePolicy.MESSAGE)
		String nickname
) {
}
