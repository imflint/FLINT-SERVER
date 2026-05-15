package kr.flint.api.domain.terms.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.terms.controller.spec.TermsControllerDocs;
import kr.flint.api.domain.terms.dto.request.TermsAgreementReq;
import kr.flint.api.domain.terms.service.TermsCommandFacade;
import kr.flint.api.domain.terms.service.TermsQueryFacade;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsListRes;
import kr.flint.terms.dto.response.TermsRes;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/terms")
public class TermsController implements TermsControllerDocs {

	private final TermsQueryFacade termsQueryFacade;
	private final TermsCommandFacade termsCommandFacade;

	@Override
	@GetMapping
	public ResponseEntity<SuccessResponse<TermsListRes>> getTerms(
		@RequestParam(defaultValue = "SIGNUP") TermsContext context,
		@RequestParam(required = false) TermsType type
	) {
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, termsQueryFacade.getTerms(context, type)));
	}

	@Override
	@GetMapping("/{termsId}")
	public ResponseEntity<SuccessResponse<TermsRes>> getTerms(
		@PathVariable Long termsId
	) {
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, termsQueryFacade.getTerms(termsId)));
	}

	@Override
	@PostMapping("/agreements")
	public ResponseEntity<SuccessResponse<Void>> agreeTerms(
		@CurrentUser Long userId,
		@Valid @RequestBody TermsAgreementReq request
	) {
		termsCommandFacade.agreeSignupTerms(userId, request);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_UPDATE));
	}
}
