package kr.flint.terms.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
	@UniqueConstraint(name = "uk_user_terms_agreement_user_terms", columnNames = {"user_id", "terms_id"})
})
public class UserTermsAgreement extends Base {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "terms_id", nullable = false)
	private Long termsId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime agreedAt;

	@Builder(access = AccessLevel.PRIVATE)
	private UserTermsAgreement(Long userId, Long termsId, LocalDateTime agreedAt) {
		this.userId = userId;
		this.termsId = termsId;
		this.agreedAt = agreedAt;
	}

	public static UserTermsAgreement create(Long userId, Long termsId) {
		return create(userId, termsId, LocalDateTime.now());
	}

	public static UserTermsAgreement create(Long userId, Long termsId, LocalDateTime agreedAt) {
		return UserTermsAgreement.builder()
			.userId(userId)
			.termsId(termsId)
			.agreedAt(agreedAt)
			.build();
	}
}
