package kr.flint.admin.domain.terms.service;

import org.springframework.stereotype.Component;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.terms.dto.request.TermsCreateReq;
import kr.flint.terms.dto.response.TermsRes;
import kr.flint.terms.service.TermsService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TermsCommandFacade {

    private final TermsService termsService;
    private final AdminAuthorizationService adminAuthorizationService;

    public TermsRes createTerms(Long adminId, TermsCreateReq request) {
        adminAuthorizationService.validateAdmin(adminId);
        return TermsRes.from(termsService.createTermsVersion(
            request.contextOrDefault(),
            request.type(),
            request.version(),
            request.title(),
            request.content(),
            request.required(),
            request.activeAt()
        ));
    }
}
