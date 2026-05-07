package kr.flint.admin.domain.terms.service;

import org.springframework.stereotype.Component;

import kr.flint.admin.domain.terms.dto.request.TermsCreateReq;
import kr.flint.terms.dto.response.TermsRes;
import kr.flint.terms.exception.TermsErrorCode;
import kr.flint.terms.exception.TermsException;
import kr.flint.terms.service.TermsService;
import kr.flint.user.domain.UserRole;
import kr.flint.user.dto.response.UserAuthInfo;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TermsCommandFacade {

	private final TermsService termsService;
	private final UserService userService;

	public TermsRes createTerms(Long adminUserId, TermsCreateReq request) {
		validateAdmin(adminUserId);
		return TermsRes.from(termsService.createTermsVersion(
			request.type(),
			request.title(),
			request.content(),
			request.required(),
			request.activeAt()
		));
	}

	private void validateAdmin(Long userId) {
		UserAuthInfo authInfo = userService.getAuthInfo(userId);
		if (!UserRole.ADMIN.name().equals(authInfo.role())) {
			throw new TermsException(TermsErrorCode.FORBIDDEN_TERMS_ADMIN);
		}
	}
}
