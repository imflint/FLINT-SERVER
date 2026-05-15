package kr.flint.admin.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
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

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.terms.dto.request.TermsCreateReq;
import kr.flint.adminauth.exception.AdminErrorCode;
import kr.flint.adminauth.exception.AdminException;
import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsRes;
import kr.flint.terms.service.TermsService;

@ExtendWith(MockitoExtension.class)
class TermsCommandFacadeTest {

    @Mock
    private TermsService termsService;

    @Mock
    private AdminAuthorizationService adminAuthorizationService;

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
        @DisplayName("관리자 검증에 실패하면 약관을 생성할 수 없음")
        void adminValidationFailure() {
            // given
            LocalDateTime activeAt = LocalDateTime.now();
            TermsCreateReq request = new TermsCreateReq(TermsType.SERVICE, TermsContext.SIGNUP, 1, "서비스 이용약관", "content", true, activeAt);
            doThrow(new AdminException(AdminErrorCode.ADMIN_NOT_FOUND))
                .when(adminAuthorizationService)
                .validateAdmin(10L);

            // when & then
            assertThatThrownBy(() -> termsCommandFacade.createTerms(10L, request))
                .isInstanceOf(AdminException.class)
                .extracting("errorCode")
                .isEqualTo(AdminErrorCode.ADMIN_NOT_FOUND);
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
