package kr.flint.terms.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = {
	@UniqueConstraint(name = "uk_user_terms_agreement_user_terms_context", columnNames = {"user_id", "terms_id", "context"})
})
public class UserTermsAgreement extends Base {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "terms_id", nullable = false)
	private Long termsId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TermsContext context;

	@Column(nullable = false, updatable = false)
	private LocalDateTime agreedAt;

	@Builder(access = AccessLevel.PRIVATE)
	private UserTermsAgreement(Long userId, Long termsId, TermsContext context, LocalDateTime agreedAt) {
		this.userId = userId;
		this.termsId = termsId;
		this.context = context;
		this.agreedAt = agreedAt;
	}

	public static UserTermsAgreement create(Long userId, Long termsId) {
		return create(userId, TermsContext.SIGNUP, termsId, LocalDateTime.now());
	}

	public static UserTermsAgreement create(Long userId, TermsContext context, Long termsId) {
		return create(userId, context, termsId, LocalDateTime.now());
	}

	public static UserTermsAgreement create(Long userId, Long termsId, LocalDateTime agreedAt) {
		return create(userId, TermsContext.SIGNUP, termsId, agreedAt);
	}

	public static UserTermsAgreement create(Long userId, TermsContext context, Long termsId, LocalDateTime agreedAt) {
		return UserTermsAgreement.builder()
			.userId(userId)
			.termsId(termsId)
			.context(context == null ? TermsContext.SIGNUP : context)
			.agreedAt(agreedAt)
			.build();
	}

	public TermsContext getEffectiveContext() {
		return context == null ? TermsContext.SIGNUP : context;
	}
}
