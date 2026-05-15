package kr.flint.terms.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.TermsType;

@Schema(description = "약관 응답")
public record TermsRes(
	@Schema(description = "약관 ID", example = "1")
	Long id,
	@Schema(description = "약관 유형", example = "SERVICE")
	TermsType type,
	@Schema(description = "약관 사용 맥락", example = "SIGNUP")
	TermsContext context,
	@Schema(description = "약관 버전", example = "1")
	Integer version,
	@Schema(description = "약관 제목", example = "서비스 이용약관")
	String title,
	@Schema(description = "약관 본문")
	String content,
	@Schema(description = "필수 동의 여부", example = "true")
	boolean required,
	@Schema(description = "활성 시각", example = "2026-05-01T00:00:00")
	LocalDateTime activeAt
) {
	public static TermsRes from(Terms terms) {
		return new TermsRes(
			terms.getId(),
			terms.getType(),
			terms.getEffectiveContext(),
			terms.getVersion(),
			terms.getTitle(),
			terms.getContent(),
			terms.isRequired(),
			terms.getActiveAt()
		);
	}
}
