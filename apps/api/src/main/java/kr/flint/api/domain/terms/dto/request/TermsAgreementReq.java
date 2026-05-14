package kr.flint.api.domain.terms.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "약관 동의 요청")
public record TermsAgreementReq(
	@Schema(description = "동의한 약관 ID 목록", example = "[\"1\", \"2\"]", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotEmpty(message = "동의한 약관 ID 목록은 필수입니다.")
	List<@NotBlank(message = "약관 ID는 비어 있을 수 없습니다.") String> agreedTermsIds
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
