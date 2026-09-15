package kr.flint.batch.job.ott;

import org.springframework.batch.item.ItemProcessor;

import feign.FeignException;
import kr.flint.content.domain.MediaType;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbOttRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class TmdbOttProvidersProcessor implements ItemProcessor<OttSyncContentRow, OttSyncDraft> {

	private final TmdbClient tmdbClient;

	@Override
	public OttSyncDraft process(OttSyncContentRow row) {
		if (row == null) {
			return null;
		}
		try {
			TmdbOttRes res = row.mediaType() == MediaType.TV
				? tmdbClient.getTvWatchProviders(row.tmdbId())
				: tmdbClient.getMovieWatchProviders(row.tmdbId());

			return TmdbOttSnapshot.from(res).toDraft(row.contentId());
		} catch (FeignException.NotFound nf) {
			log.debug("watch providers not found tmdbId={} mediaType={}", row.tmdbId(), row.mediaType());
			return null;
		}
	}
}
