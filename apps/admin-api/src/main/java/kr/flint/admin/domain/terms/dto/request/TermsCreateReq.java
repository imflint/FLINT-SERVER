package kr.flint.admin.domain.terms.dto.request;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import kr.flint.terms.domain.TermsType;

@Schema(description = "약관 생성 요청")
public record TermsCreateReq(
	@Schema(description = "약관 유형", example = "SERVICE", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "약관 유형은 필수입니다.")
	TermsType type,

	@Schema(description = "약관 버전", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "약관 버전은 필수입니다.")
	@Positive(message = "약관 버전은 1 이상이어야 합니다.")
	Integer version,

	@Schema(description = "약관 제목", example = "서비스 이용약관", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "약관 제목은 필수입니다.")
	@Size(max = 100, message = "약관 제목은 100자 이하여야 합니다.")
	String title,

	@Schema(description = "약관 본문", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "약관 본문은 필수입니다.")
	String content,

	@Schema(description = "필수 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "필수 동의 여부는 필수입니다.")
	Boolean required,

	@Schema(description = "활성 시각", example = "2026-05-01T00:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "활성 시각은 필수입니다.")
	LocalDateTime activeAt
) {
}
