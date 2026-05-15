package kr.flint.terms.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.terms.domain.Terms;

@Schema(description = "약관 동의 상태 응답")
public record TermsAgreementStatusRes(
	@Schema(description = "필수 약관 추가 동의 필요 여부", example = "true")
	boolean requiredTermsAgreementNeeded,
	@Schema(description = "아직 동의하지 않은 현재 활성 필수 약관 목록")
	List<TermsRes> pendingRequiredTerms
) {
	public static TermsAgreementStatusRes from(List<Terms> pendingRequiredTerms) {
		List<TermsRes> terms = pendingRequiredTerms.stream()
			.map(TermsRes::from)
			.toList();
		return new TermsAgreementStatusRes(!terms.isEmpty(), terms);
	}
}
