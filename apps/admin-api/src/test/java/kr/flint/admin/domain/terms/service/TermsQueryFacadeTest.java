package kr.flint.admin.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.terms.dto.request.TermsListSort;
import kr.flint.adminauth.exception.AdminErrorCode;
import kr.flint.adminauth.exception.AdminException;
import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsRes;
import kr.flint.terms.repository.TermsRepository;

@ExtendWith(MockitoExtension.class)
class TermsQueryFacadeTest {

    @Mock
    private TermsRepository termsRepository;

    @Mock
    private AdminAuthorizationService adminAuthorizationService;

    @InjectMocks
    private TermsQueryFacade termsQueryFacade;

    @Nested
    @DisplayName("getTerms")
    class GetTerms {

        @Test
        @DisplayName("약관 유형을 지정하지 않으면 전체 약관을 버전순으로 조회")
        void getAllTerms() {
            // given
            Terms serviceTerms = createTerms(1L, TermsType.SERVICE, 2);
            when(termsRepository.findAll(Sort.by(Sort.Direction.DESC, "version")
                .and(Sort.by(Sort.Direction.ASC, "type"))
                .and(Sort.by(Sort.Direction.DESC, "id"))))
                .thenReturn(List.of(serviceTerms));

            // when
            List<TermsRes> result = termsQueryFacade.getTerms(10L, null, TermsListSort.VERSION, Sort.Direction.DESC);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().id()).isEqualTo(1L);
            assertThat(result.getFirst().type()).isEqualTo(TermsType.SERVICE);
            assertThat(result.getFirst().version()).isEqualTo(2);
        }

        @Test
        @DisplayName("약관 유형을 지정하면 해당 유형만 조회")
        void getTermsByType() {
            // given
            Terms privacyTerms = createTerms(2L, TermsType.PRIVACY, 1);
            when(termsRepository.findByType(TermsType.PRIVACY, Sort.by(Sort.Direction.ASC, "type")
                .and(Sort.by(Sort.Direction.DESC, "version"))
                .and(Sort.by(Sort.Direction.DESC, "id"))))
                .thenReturn(List.of(privacyTerms));

            // when
            List<TermsRes> result = termsQueryFacade.getTerms(10L, TermsType.PRIVACY, TermsListSort.TYPE, Sort.Direction.ASC);

            // then
            assertThat(result).extracting(TermsRes::type).containsExactly(TermsType.PRIVACY);
        }

        @Test
        @DisplayName("관리자 검증에 실패하면 약관을 조회할 수 없음")
        void adminValidationFailure() {
            // given
            doThrow(new AdminException(AdminErrorCode.ADMIN_NOT_FOUND))
                .when(adminAuthorizationService)
                .validateAdmin(10L);

            // when & then
            assertThatThrownBy(() -> termsQueryFacade.getTerms(10L, null, null, null))
                .isInstanceOf(AdminException.class)
                .extracting("errorCode")
                .isEqualTo(AdminErrorCode.ADMIN_NOT_FOUND);
            verifyNoInteractions(termsRepository);
        }
    }

    private Terms createTerms(Long id, TermsType type, int version) {
        Terms terms = Terms.create(TermsContext.SIGNUP, type, version, type.getDescription(), "content", true, LocalDateTime.now());
        ReflectionTestUtils.setField(terms, "id", id);
        return terms;
    }
}
