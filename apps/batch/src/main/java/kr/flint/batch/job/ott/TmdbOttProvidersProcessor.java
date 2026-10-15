package kr.flint.batch.job.ott;

import org.springframework.batch.item.ItemProcessor;

import kr.flint.batch.service.TmdbApiException;
import kr.flint.batch.service.TmdbApiExecutor;
import kr.flint.content.domain.MediaType;
import kr.flint.infra.tmdb.client.TmdbClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class TmdbOttProvidersProcessor implements ItemProcessor<OttSyncContentRow, OttSyncDraft> {

	private final TmdbClient tmdbClient;
	private final TmdbApiExecutor apiExecutor;

	@Override
	public OttSyncDraft process(OttSyncContentRow row) {
		if (row == null) {
			return null;
		}
		try {
			return apiExecutor.execute("watchProviders:" + row.mediaType() + ":" + row.tmdbId(), () ->
				TmdbOttSnapshot.from(row.mediaType() == MediaType.TV
					? tmdbClient.getTvWatchProviders(row.tmdbId())
					: tmdbClient.getMovieWatchProviders(row.tmdbId())).toDraft(row.contentId()));
		} catch (TmdbApiException failure) {
			if (!failure.notFound() && !failure.retryable()) throw failure;
			log.warn("watch providers retained tmdbId={} mediaType={} cause={}", row.tmdbId(), row.mediaType(), failure.getMessage());
			return null;
		}
	}
}
