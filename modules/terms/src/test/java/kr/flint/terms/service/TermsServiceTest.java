package kr.flint.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.domain.UserTermsAgreement;
import kr.flint.terms.exception.TermsErrorCode;
import kr.flint.terms.exception.TermsException;
import kr.flint.terms.repository.TermsRepository;
import kr.flint.terms.repository.UserTermsAgreementRepository;

@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

	@Mock
	private TermsRepository termsRepository;

	@Mock
	private UserTermsAgreementRepository userTermsAgreementRepository;

	@InjectMocks
	private TermsService termsService;

	@Nested
	@DisplayName("getCurrentTerms")
	class GetCurrentTerms {

		@Test
		@DisplayName("유형별 최신 활성 약관만 반환")
		void latestActiveTermsByType() {
			// given
			Terms oldService = createTerms(1L, TermsType.SERVICE, 1, true, LocalDateTime.now().minusDays(10));
			Terms newService = createTerms(2L, TermsType.SERVICE, 2, true, LocalDateTime.now().minusDays(1));
			Terms privacy = createTerms(3L, TermsType.PRIVACY, 1, true, LocalDateTime.now().minusDays(2));
			when(termsRepository.findByActiveAtLessThanEqual(any(LocalDateTime.class)))
				.thenReturn(List.of(oldService, privacy, newService));

			// when
			List<Terms> result = termsService.getCurrentTerms(null);

			// then
			assertThat(result).extracting(Terms::getId).containsExactly(2L, 3L);
		}
	}

	@Nested
	@DisplayName("createTermsVersion")
	class CreateTermsVersion {

		@Test
		@DisplayName("같은 유형의 중복 버전은 생성할 수 없음")
		void duplicateVersion() {
			// given
			when(termsRepository.existsByTypeAndVersion(TermsType.SERVICE, 1))
				.thenReturn(true);

			// when & then
			assertThatThrownBy(() -> termsService.createTermsVersion(
				TermsType.SERVICE,
				1,
				"서비스 이용약관",
				"content",
				true,
				LocalDateTime.now()
			))
				.isInstanceOf(TermsException.class)
				.extracting("errorCode")
				.isEqualTo(TermsErrorCode.DUPLICATE_TERMS_VERSION);
			verify(termsRepository, never()).save(any(Terms.class));
		}
	}

	@Nested
	@DisplayName("validateAndCreateAgreements")
	class ValidateAndCreateAgreements {

		@Test
		@DisplayName("필수 약관과 선택 약관 동의를 저장")
		void success() {
			// given
			Terms service = createTerms(1L, TermsType.SERVICE, 1, true, LocalDateTime.now().minusDays(1));
			Terms privacy = createTerms(2L, TermsType.PRIVACY, 1, true, LocalDateTime.now().minusDays(1));
			Terms marketing = createTerms(3L, TermsType.MARKETING, 1, false, LocalDateTime.now().minusDays(1));
			when(termsRepository.findByActiveAtLessThanEqual(any(LocalDateTime.class)))
				.thenReturn(List.of(service, privacy, marketing));

			// when
			termsService.validateAndCreateAgreements(100L, List.of(1L, 2L, 3L));

			// then
			verify(userTermsAgreementRepository).saveAll(argThat(agreements -> {
				assertThat(agreements).hasSize(3);
				assertThat(agreements).extracting(UserTermsAgreement::getTermsId)
					.containsExactly(1L, 2L, 3L);
				return true;
			}));
		}

		@Test
		@DisplayName("필수 약관 누락 시 예외")
		void missingRequiredTerms() {
			// given
			Terms service = createTerms(1L, TermsType.SERVICE, 1, true, LocalDateTime.now().minusDays(1));
			Terms privacy = createTerms(2L, TermsType.PRIVACY, 1, true, LocalDateTime.now().minusDays(1));
			when(termsRepository.findByActiveAtLessThanEqual(any(LocalDateTime.class)))
				.thenReturn(List.of(service, privacy));

			// when & then
			assertThatThrownBy(() -> termsService.validateAndCreateAgreements(100L, List.of(1L)))
				.isInstanceOf(TermsException.class)
				.extracting("errorCode")
				.isEqualTo(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
		}

		@Test
		@DisplayName("현재 활성 약관이 아닌 ID 포함 시 예외")
		void invalidTermsId() {
			// given
			Terms service = createTerms(2L, TermsType.SERVICE, 1, true, LocalDateTime.now().minusDays(1));
			Terms privacy = createTerms(3L, TermsType.PRIVACY, 1, true, LocalDateTime.now().minusDays(1));
			when(termsRepository.findByActiveAtLessThanEqual(any(LocalDateTime.class)))
				.thenReturn(List.of(service, privacy));

			// when & then
			assertThatThrownBy(() -> termsService.validateAndCreateAgreements(100L, List.of(1L, 2L, 3L)))
				.isInstanceOf(TermsException.class)
				.extracting("errorCode")
				.isEqualTo(TermsErrorCode.INVALID_TERMS_AGREEMENT);
		}

		@Test
		@DisplayName("활성 필수 약관이 없으면 예외")
		void noActiveRequiredTerms() {
			// given
			Terms marketing = createTerms(3L, TermsType.MARKETING, 1, false, LocalDateTime.now().minusDays(1));
			when(termsRepository.findByActiveAtLessThanEqual(any(LocalDateTime.class)))
				.thenReturn(List.of(marketing));

			// when & then
			assertThatThrownBy(() -> termsService.validateAndCreateAgreements(100L, List.of(3L)))
				.isInstanceOf(TermsException.class)
				.extracting("errorCode")
				.isEqualTo(TermsErrorCode.NO_ACTIVE_REQUIRED_TERMS);
		}
	}

	private Terms createTerms(Long id, TermsType type, Integer version, boolean required, LocalDateTime activeAt) {
		Terms terms = Terms.create(type, version, type.getDescription(), "content", required, activeAt);
		ReflectionTestUtils.setField(terms, "id", id);
		return terms;
	}
}
