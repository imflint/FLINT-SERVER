package kr.flint.api.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import feign.FeignException;
import kr.flint.content.exception.ContentErrorCode;
import kr.flint.content.exception.ContentException;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbOttRes;
import kr.flint.ott.domain.OttContent;
import kr.flint.ott.domain.OttProvider;
import kr.flint.ott.repository.OttContentRepository;
import kr.flint.ott.repository.OttProviderRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class OttCommandFacade {
	private final TmdbClient tmdbClient;                 // infra
	private final OttProviderRepository ottProviderRepository; // ott
	private final OttContentRepository ottContentRepository;   // ott

	public void mapContentOtt(Long contentId, Long tmdbId, String mediaType) {

		try {
			TmdbOttRes res = tmdbClient.getMovieWatchProviders(tmdbId);
			var country = (res.results() == null) ? null : res.results().get("KR");
			if (country == null || country.flatrate() == null)
				return;

			for (var p : country.flatrate()) {
				OttProvider provider = ottProviderRepository.findByName(p.providerName())
					.orElse(null);
				if (provider == null)
					continue;

				if (ottContentRepository.existsByOttProviderAndContentId(provider, contentId)) {
					continue;
				}
				ottContentRepository.save(OttContent.create(provider, contentId, provider.getUrl()));
			}
		} catch (Exception e) {
			throw new ContentException(ContentErrorCode.TMDB_OTT_NOT_FOUND);
		}

	}

}
