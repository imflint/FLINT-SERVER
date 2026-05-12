package kr.flint.terms.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약관 목록 응답")
public record TermsListRes(
	@Schema(description = "약관 목록")
	List<TermsRes> terms
) {
	public static TermsListRes from(List<TermsRes> terms) {
		return new TermsListRes(terms);
	}
}
