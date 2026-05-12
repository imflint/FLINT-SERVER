package kr.flint.batch.job.ott;

import java.util.List;

import org.springframework.batch.item.ItemProcessor;

import feign.FeignException;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbOttRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class TmdbOttProvidersProcessor implements ItemProcessor<Content, OttSyncDraft> {

	private static final String COUNTRY = "KR";
	private final TmdbClient tmdbClient;

	@Override
	public OttSyncDraft process(Content content) {
		if (content == null) {
			return null;
		}
		try {
			TmdbOttRes res = content.getMediaType() == MediaType.TV
				? tmdbClient.getTvWatchProviders(content.getTmdbId())
				: tmdbClient.getMovieWatchProviders(content.getTmdbId());

			TmdbOttRes.CountryProvider country = (res.results() == null) ? null : res.results().get(COUNTRY);
			if (country == null || country.flatrate() == null || country.flatrate().isEmpty()) {
				return null;
			}
			List<String> providerNames = country.flatrate().stream()
				.map(TmdbOttRes.Provider::providerName)
				.toList();
			return new OttSyncDraft(content.getId(), providerNames);
		} catch (FeignException.NotFound nf) {
			log.debug("watch providers not found tmdbId={} mediaType={}", content.getTmdbId(), content.getMediaType());
			return null;
		}
	}
}
