package kr.flint.terms.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_terms_context_type_version", columnNames = {"context", "type", "version"})
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms extends BaseTime {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TermsType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TermsContext context;

	@Column(nullable = false)
	private Integer version;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "is_required", nullable = false)
	private boolean required;

	@Column(nullable = false)
	private LocalDateTime activeAt;

	@Builder(access = AccessLevel.PRIVATE)
	private Terms(
		TermsContext context,
		TermsType type,
		Integer version,
		String title,
		String content,
		boolean required,
		LocalDateTime activeAt
	) {
		this.context = context;
		this.type = type;
		this.version = version;
		this.title = title;
		this.content = content;
		this.required = required;
		this.activeAt = activeAt;
	}

	public static Terms create(
		TermsType type,
		Integer version,
		String title,
		String content,
		boolean required,
		LocalDateTime activeAt
	) {
		return create(TermsContext.SIGNUP, type, version, title, content, required, activeAt);
	}

	public static Terms create(
		TermsContext context,
		TermsType type,
		Integer version,
		String title,
		String content,
		boolean required,
		LocalDateTime activeAt
	) {
		return Terms.builder()
			.context(context == null ? TermsContext.SIGNUP : context)
			.type(type)
			.version(version)
			.title(title)
			.content(content)
			.required(required)
			.activeAt(activeAt)
			.build();
	}

	public boolean isActive(LocalDateTime now) {
		return !activeAt.isAfter(now);
	}

	public boolean isNewerThan(Terms other) {
		return version > other.version;
	}

	public TermsContext getEffectiveContext() {
		return context == null ? TermsContext.SIGNUP : context;
	}
}
