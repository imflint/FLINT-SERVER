package kr.flint.admin.domain.terms.controller;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.admin.domain.terms.controller.spec.TermsAdminControllerDocs;
import kr.flint.admin.domain.terms.dto.request.TermsCreateReq;
import kr.flint.admin.domain.terms.dto.request.TermsListSort;
import kr.flint.admin.domain.terms.service.TermsCommandFacade;
import kr.flint.admin.domain.terms.service.TermsQueryFacade;
import kr.flint.admin.global.security.annotation.CurrentAdmin;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsRes;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/terms")
public class TermsAdminController implements TermsAdminControllerDocs {

    private final TermsCommandFacade termsCommandFacade;
    private final TermsQueryFacade termsQueryFacade;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<List<TermsRes>>> getTerms(
        @CurrentAdmin Long adminId,
        @RequestParam(required = false) TermsType type,
        @RequestParam(required = false) TermsListSort sortBy,
        @RequestParam(required = false) Sort.Direction direction
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            termsQueryFacade.getTerms(adminId, type, sortBy, direction)
        ));
    }

    @Override
    @PostMapping
    public ResponseEntity<SuccessResponse<TermsRes>> createTerms(
        @CurrentAdmin Long adminId,
        @Valid @RequestBody TermsCreateReq request
    ) {
        return ResponseEntity
            .status(SuccessCode.SUCCESS_CREATE.getHttpStatus())
            .body(SuccessResponse.of(SuccessCode.SUCCESS_CREATE, termsCommandFacade.createTerms(adminId, request)));
    }
}
