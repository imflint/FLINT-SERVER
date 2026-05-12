package kr.flint.api.domain.terms.service;

import org.springframework.stereotype.Component;

import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsListRes;
import kr.flint.terms.dto.response.TermsRes;
import kr.flint.terms.service.TermsService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TermsQueryFacade {

	private final TermsService termsService;

	public TermsListRes getTerms(TermsType type) {
		return TermsListRes.from(termsService.getCurrentTerms(type).stream()
			.map(TermsRes::from)
			.toList());
	}

	public TermsRes getTerms(Long termsId) {
		return TermsRes.from(termsService.getById(termsId));
	}
}
