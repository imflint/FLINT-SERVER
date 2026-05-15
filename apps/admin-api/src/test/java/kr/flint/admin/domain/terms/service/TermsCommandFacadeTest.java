package kr.flint.admin.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.admin.domain.terms.dto.request.TermsCreateReq;
import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsRes;
import kr.flint.terms.exception.TermsErrorCode;
import kr.flint.terms.exception.TermsException;
import kr.flint.terms.service.TermsService;
import kr.flint.user.dto.response.UserAuthInfo;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class TermsCommandFacadeTest {

	@Mock
	private TermsService termsService;

	@Mock
	private UserService userService;

	@InjectMocks
	private TermsCommandFacade termsCommandFacade;

	@Nested
	@DisplayName("createTerms")
	class CreateTerms {

		@Test
		@DisplayName("ADMIN은 새 약관 버전을 생성")
		void adminSuccess() {
			// given
			LocalDateTime activeAt = LocalDateTime.now();
			TermsCreateReq request = new TermsCreateReq(TermsType.SERVICE, TermsContext.SIGNUP, 1, "서비스 이용약관", "content", true, activeAt);
			Terms terms = Terms.create(TermsContext.SIGNUP, TermsType.SERVICE, 1, "서비스 이용약관", "content", true, activeAt);
			ReflectionTestUtils.setField(terms, "id", 1L);
			when(userService.getAuthInfo(10L)).thenReturn(UserAuthInfo.of(10L, "admin", "ADMIN"));
			when(termsService.createTermsVersion(TermsContext.SIGNUP, TermsType.SERVICE, 1, "서비스 이용약관", "content", true, activeAt))
				.thenReturn(terms);

			// when
			TermsRes result = termsCommandFacade.createTerms(10L, request);

			// then
			assertThat(result.id()).isEqualTo(1L);
			assertThat(result.context()).isEqualTo(TermsContext.SIGNUP);
			assertThat(result.type()).isEqualTo(TermsType.SERVICE);
			assertThat(result.version()).isEqualTo(1);
		}

		@Test
		@DisplayName("ADMIN이 아니면 약관을 생성할 수 없음")
		void nonAdminForbidden() {
			// given
			LocalDateTime activeAt = LocalDateTime.now();
			TermsCreateReq request = new TermsCreateReq(TermsType.SERVICE, TermsContext.SIGNUP, 1, "서비스 이용약관", "content", true, activeAt);
			when(userService.getAuthInfo(10L)).thenReturn(UserAuthInfo.of(10L, "user", "FLING"));

			// when & then
			assertThatThrownBy(() -> termsCommandFacade.createTerms(10L, request))
				.isInstanceOf(TermsException.class)
				.extracting("errorCode")
				.isEqualTo(TermsErrorCode.FORBIDDEN_TERMS_ADMIN);
			verify(termsService, never()).createTermsVersion(
				TermsContext.SIGNUP,
				TermsType.SERVICE,
				1,
				"서비스 이용약관",
				"content",
				true,
				activeAt
			);
		}
	}
}
