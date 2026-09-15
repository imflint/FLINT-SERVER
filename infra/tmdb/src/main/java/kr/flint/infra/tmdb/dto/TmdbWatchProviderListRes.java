package kr.flint.infra.tmdb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TmdbWatchProviderListRes(
	List<Provider> results
) {
	public record Provider(
		@JsonProperty("provider_id") Long providerId,
		@JsonProperty("provider_name") String providerName,
		@JsonProperty("logo_path") String logoPath,
		@JsonProperty("display_priority") Integer displayPriority
	) {
	}
}
