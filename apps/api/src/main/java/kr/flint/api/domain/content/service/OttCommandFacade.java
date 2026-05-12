package kr.flint.api.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.content.exception.ContentErrorCode;
import kr.flint.content.exception.ContentException;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbOttRes;
import kr.flint.ott.service.OttSyncService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class OttCommandFacade {
	private final TmdbClient tmdbClient;
	private final OttSyncService ottSyncService;

	public void mapContentOtt(Long contentId, Long tmdbId, String mediaType) {
		try {
			TmdbOttRes res = "tv".equals(mediaType)
				? tmdbClient.getTvWatchProviders(tmdbId)
				: tmdbClient.getMovieWatchProviders(tmdbId);
			TmdbOttRes.CountryProvider country = (res.results() == null) ? null : res.results().get("KR");
			if (country == null || country.flatrate() == null) {
				return;
			}

			List<String> providerNames = country.flatrate().stream()
				.map(TmdbOttRes.Provider::providerName)
				.toList();
			ottSyncService.linkProviders(contentId, providerNames);
		} catch (Exception e) {
			throw new ContentException(ContentErrorCode.TMDB_OTT_NOT_FOUND);
		}
	}
}
