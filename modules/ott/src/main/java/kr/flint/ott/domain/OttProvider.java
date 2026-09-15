package kr.flint.ott.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class OttProvider extends Base {
	@Column(nullable = false)
	private String name;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String logoUrl;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String url;

	@Column(name = "tmdb_provider_id", unique = true)
	private Long tmdbProviderId;

	@Column(name = "display_priority", nullable = false)
	private int displayPriority;

	@Column(nullable = false)
	private boolean active;

	public static OttProvider create(
		String name,
		String logoUrl,
		String url,
		Long tmdbProviderId,
		int displayPriority
	) {
		return new OttProvider(name, logoUrl, url, tmdbProviderId, displayPriority, true);
	}

	public void synchronize(String name, String logoUrl, int displayPriority, boolean active) {
		this.name = name;
		this.logoUrl = logoUrl;
		this.displayPriority = displayPriority;
		this.active = active;
	}
}
