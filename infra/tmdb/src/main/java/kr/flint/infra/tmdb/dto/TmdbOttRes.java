package kr.flint.infra.tmdb.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TmdbOttRes(
	Long id,
	Map<String, CountryProvider> results
) {
	public record CountryProvider(
		String link,
		List<Provider> flatrate
	) {}

	public record Provider(
		@JsonProperty("provider_id")
		Long providerId,

		@JsonProperty("provider_name")
		String providerName,

		@JsonProperty("logo_path")
		String logoPath,

		@JsonProperty("display_priority")
		Integer displayPriority
	) {}
}
