package kr.flint.terms.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms extends BaseTime {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TermsType type;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "is_required", nullable = false)
	private boolean required;

	@Column(nullable = false)
	private LocalDateTime activeAt;

	@Builder(access = AccessLevel.PRIVATE)
	private Terms(TermsType type, String title, String content, boolean required, LocalDateTime activeAt) {
		this.type = type;
		this.title = title;
		this.content = content;
		this.required = required;
		this.activeAt = activeAt;
	}

	public static Terms create(TermsType type, String title, String content, boolean required, LocalDateTime activeAt) {
		return Terms.builder()
			.type(type)
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
		return activeAt.isAfter(other.activeAt);
	}
}
