package kr.flint.terms.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.domain.UserTermsAgreement;
import kr.flint.terms.exception.TermsErrorCode;
import kr.flint.terms.exception.TermsException;
import kr.flint.terms.repository.TermsRepository;
import kr.flint.terms.repository.UserTermsAgreementRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsService {

	private final TermsRepository termsRepository;
	private final UserTermsAgreementRepository userTermsAgreementRepository;

	public List<Terms> getCurrentTerms(@Nullable TermsType type) {
		return getCurrentTerms(TermsContext.SIGNUP, type);
	}

	public List<Terms> getCurrentTerms(TermsContext context, @Nullable TermsType type) {
		LocalDateTime now = LocalDateTime.now();
		TermsContext effectiveContext = resolveContext(context);
		boolean includeLegacySignup = effectiveContext == TermsContext.SIGNUP;
		if (type != null) {
			return selectLatestByType(termsRepository.findByContextAndTypeAndActiveAtLessThanEqual(
				effectiveContext,
				type,
				now,
				includeLegacySignup
			));
		}
		return selectLatestByType(termsRepository.findByContextAndActiveAtLessThanEqual(
			effectiveContext,
			now,
			includeLegacySignup
		));
	}

	public Terms getById(Long termsId) {
		return termsRepository.findById(termsId)
			.orElseThrow(() -> new TermsException(TermsErrorCode.TERMS_NOT_FOUND));
	}

	@Transactional
	public Terms createTermsVersion(
		TermsType type,
		Integer version,
		String title,
		String content,
		boolean required,
		LocalDateTime activeAt
	) {
		return createTermsVersion(TermsContext.SIGNUP, type, version, title, content, required, activeAt);
	}

	@Transactional
	public Terms createTermsVersion(
		TermsContext context,
		TermsType type,
		Integer version,
		String title,
		String content,
		boolean required,
		LocalDateTime activeAt
	) {
		TermsContext effectiveContext = resolveContext(context);
		if (termsRepository.existsByContextAndTypeAndVersion(
			effectiveContext,
			type,
			version,
			effectiveContext == TermsContext.SIGNUP
		)) {
			throw new TermsException(TermsErrorCode.DUPLICATE_TERMS_VERSION);
		}

		return termsRepository.save(Terms.create(effectiveContext, type, version, title, content, required, activeAt));
	}

	@Transactional
	public void validateAndCreateAgreements(Long userId, List<Long> agreedTermsIds) {
		validateAndCreateAgreements(userId, TermsContext.SIGNUP, agreedTermsIds);
	}

	@Transactional
	public void validateAndCreateAgreements(Long userId, TermsContext context, List<Long> agreedTermsIds) {
		if (CollectionUtils.isEmpty(agreedTermsIds)) {
			throw new TermsException(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
		}

		TermsContext effectiveContext = resolveContext(context);
		List<Terms> currentTerms = getCurrentTerms(effectiveContext, null);
		Set<Long> currentTermsIds = extractTermsIds(currentTerms);
		Set<Long> requiredTermsIds = currentTerms.stream()
			.filter(Terms::isRequired)
			.map(Terms::getId)
			.collect(java.util.stream.Collectors.toSet());

		if (requiredTermsIds.isEmpty()) {
			throw new TermsException(TermsErrorCode.NO_ACTIVE_REQUIRED_TERMS);
		}

		Set<Long> agreedIds = new HashSet<>(agreedTermsIds);
		if (!currentTermsIds.containsAll(agreedIds)) {
			throw new TermsException(TermsErrorCode.INVALID_TERMS_AGREEMENT);
		}

		if (!agreedIds.containsAll(requiredTermsIds)) {
			throw new TermsException(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
		}

		List<UserTermsAgreement> agreements = currentTerms.stream()
			.map(Terms::getId)
			.filter(agreedIds::contains)
			.map(termsId -> UserTermsAgreement.create(userId, effectiveContext, termsId))
			.toList();

		userTermsAgreementRepository.saveAll(agreements);
	}

	private Set<Long> extractTermsIds(List<Terms> terms) {
		return terms.stream()
			.map(Terms::getId)
			.collect(java.util.stream.Collectors.toSet());
	}

	private List<Terms> selectLatestByType(List<Terms> terms) {
		Map<TermsType, Terms> latestByType = new EnumMap<>(TermsType.class);
		for (Terms term : terms) {
			latestByType.merge(term.getType(), term, (current, candidate) ->
				candidate.isNewerThan(current) ? candidate : current
			);
		}
		return Arrays.stream(TermsType.values())
			.map(latestByType::get)
			.filter(java.util.Objects::nonNull)
			.toList();
	}

	private TermsContext resolveContext(TermsContext context) {
		return context == null ? TermsContext.SIGNUP : context;
	}
}
