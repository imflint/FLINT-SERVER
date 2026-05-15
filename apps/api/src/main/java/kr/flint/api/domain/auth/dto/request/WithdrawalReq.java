package kr.flint.api.domain.auth.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "회원탈퇴 요청")
public record WithdrawalReq(
	@Schema(description = "동의한 회원탈퇴 약관 ID 목록", example = "[\"10\"]", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotEmpty(message = "동의한 회원탈퇴 약관 ID 목록은 필수입니다.")
	List<@NotNull(message = "약관 ID는 null일 수 없습니다.") String> agreedTermsIds
) {
	public List<Long> agreedTermsIdValues() {
		if (agreedTermsIds == null) {
			return List.of();
		}
		return agreedTermsIds.stream()
			.map(Long::valueOf)
			.toList();
	}
}
