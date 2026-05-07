package kr.flint.api.domain.terms.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.terms.controller.spec.TermsControllerDocs;
import kr.flint.api.domain.terms.service.TermsQueryFacade;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsRes;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/terms")
public class TermsController implements TermsControllerDocs {

	private final TermsQueryFacade termsQueryFacade;

	@Override
	@GetMapping
	public ResponseEntity<SuccessResponse<List<TermsRes>>> getTerms(
		@RequestParam(required = false) TermsType type
	) {
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, termsQueryFacade.getTerms(type)));
	}

	@Override
	@GetMapping("/{termsId}")
	public ResponseEntity<SuccessResponse<TermsRes>> getTerms(
		@PathVariable Long termsId
	) {
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, termsQueryFacade.getTerms(termsId)));
	}
}
