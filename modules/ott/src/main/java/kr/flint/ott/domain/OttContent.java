package kr.flint.ott.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Table(
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_content_ott",
			columnNames = {"content_id", "ott_provider_id"}
		)
	}
)
public class OttContent extends Base {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ott_provider_id", nullable = false)
	private OttProvider ottProvider;

	@Column(nullable = false)
	private Long contentId;

	@Column(name = "content_url", columnDefinition = "TEXT")
	private String contentUrl;

	public static OttContent create(OttProvider ottProvider, Long contentId, String contentUrl) {
		return new OttContent(ottProvider, contentId, contentUrl);
	}
}
