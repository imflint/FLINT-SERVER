package kr.flint.batch.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.batch.job.ott.OttSyncDraft;
import kr.flint.batch.repository.OttBatchJdbcRepository;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbWatchProviderListRes;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TmdbOttProviderMasterService {

	private static final String LANGUAGE = "ko-KR";
	private static final String WATCH_REGION = "KR";
	private static final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

	private final TmdbClient tmdbClient;
	private final OttBatchJdbcRepository ottBatchJdbcRepository;

	@Transactional
	public int synchronize() {
		TmdbWatchProviderListRes movie = tmdbClient.getMovieWatchProviderList(LANGUAGE, WATCH_REGION);
		TmdbWatchProviderListRes tv = tmdbClient.getTvWatchProviderList(LANGUAGE, WATCH_REGION);

		Map<Long, OttSyncDraft.Provider> providers = new LinkedHashMap<>();
		addAll(providers, movie);
		addAll(providers, tv);
		if (providers.isEmpty()) {
			throw new IllegalStateException("TMDB KR watch provider master is empty");
		}
		ottBatchJdbcRepository.synchronizeProviderMaster(List.copyOf(providers.values()));
		return providers.size();
	}

	private void addAll(Map<Long, OttSyncDraft.Provider> target, TmdbWatchProviderListRes response) {
		if (response == null || response.results() == null) {
			return;
		}
		for (TmdbWatchProviderListRes.Provider provider : response.results()) {
			if (provider.providerId() == null || provider.providerName() == null) {
				continue;
			}
			target.merge(provider.providerId(), toDraft(provider), this::preferLowerPriority);
		}
	}

	private OttSyncDraft.Provider toDraft(TmdbWatchProviderListRes.Provider provider) {
		return new OttSyncDraft.Provider(
			provider.providerId(),
			provider.providerName(),
			provider.logoPath() == null ? null : TMDB_IMAGE_BASE + provider.logoPath(),
			provider.displayPriority() == null ? Integer.MAX_VALUE : provider.displayPriority()
		);
	}

	private OttSyncDraft.Provider preferLowerPriority(
		OttSyncDraft.Provider current,
		OttSyncDraft.Provider candidate
	) {
		return candidate.displayPriority() < current.displayPriority() ? candidate : current;
	}
}
