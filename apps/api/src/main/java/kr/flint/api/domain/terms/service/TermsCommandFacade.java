package kr.flint.api.domain.terms.service;

import org.springframework.stereotype.Component;

import kr.flint.api.domain.terms.dto.request.TermsAgreementReq;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.service.TermsService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TermsCommandFacade {

	private final TermsService termsService;

	public void agreeSignupTerms(Long userId, TermsAgreementReq request) {
		termsService.validateAndCreateAgreements(userId, TermsContext.SIGNUP, request.agreedTermsIdValues());
	}
}
