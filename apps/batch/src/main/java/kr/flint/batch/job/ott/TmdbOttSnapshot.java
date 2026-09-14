package kr.flint.batch.job.ott;

import java.util.List;

import kr.flint.infra.tmdb.dto.TmdbOttRes;

public record TmdbOttSnapshot(
	String contentUrl,
	List<OttSyncDraft.Provider> providers
) {
	private static final String COUNTRY = "KR";
	private static final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

	public TmdbOttSnapshot {
		providers = providers == null ? List.of() : List.copyOf(providers);
	}

	public static TmdbOttSnapshot from(TmdbOttRes response) {
		TmdbOttRes.CountryProvider country = response == null || response.results() == null
			? null
			: response.results().get(COUNTRY);
		if (country == null) {
			return new TmdbOttSnapshot(null, List.of());
		}

		List<OttSyncDraft.Provider> providers = country.flatrate() == null
			? List.of()
			: country.flatrate().stream()
				.filter(provider -> provider.providerId() != null)
				.map(provider -> new OttSyncDraft.Provider(
					provider.providerId(),
					provider.providerName(),
					provider.logoPath() == null ? null : TMDB_IMAGE_BASE + provider.logoPath(),
					provider.displayPriority() == null ? Integer.MAX_VALUE : provider.displayPriority()
				))
				.toList();
		return new TmdbOttSnapshot(country.link(), providers);
	}

	public OttSyncDraft toDraft(Long contentId) {
		return new OttSyncDraft(contentId, contentUrl, providers);
	}
}
