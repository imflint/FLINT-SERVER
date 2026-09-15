package kr.flint.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.batch.job.ott.OttSyncDraft;
import kr.flint.batch.repository.OttBatchJdbcRepository;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbWatchProviderListRes;

@ExtendWith(MockitoExtension.class)
class TmdbOttProviderMasterServiceTest {

	@Mock
	private TmdbClient tmdbClient;

	@Mock
	private OttBatchJdbcRepository repository;

	private TmdbOttProviderMasterService service;

	@BeforeEach
	void setUp() {
		service = new TmdbOttProviderMasterService(tmdbClient, repository);
	}

	@Test
	void synchronizesMovieAndTvUnionUsingLowestDisplayPriority() {
		when(tmdbClient.getMovieWatchProviderList("ko-KR", "KR")).thenReturn(response(
			provider(8L, "Netflix", "/netflix.png", 5),
			provider(337L, "Disney Plus", "/disney.png", 2)
		));
		when(tmdbClient.getTvWatchProviderList("ko-KR", "KR")).thenReturn(response(
			provider(8L, "Netflix", "/netflix-tv.png", 1),
			provider(97L, "Watcha", "/watcha.png", 3)
		));

		int count = service.synchronize();

		ArgumentCaptor<List<OttSyncDraft.Provider>> captor = ArgumentCaptor.forClass(List.class);
		verify(repository).synchronizeProviderMaster(captor.capture());
		assertThat(count).isEqualTo(3);
		assertThat(captor.getValue())
			.extracting(OttSyncDraft.Provider::tmdbProviderId)
			.containsExactly(8L, 337L, 97L);
		assertThat(captor.getValue().getFirst().displayPriority()).isEqualTo(1);
	}

	@Test
	void emptyMasterDoesNotDeactivateExistingProviders() {
		when(tmdbClient.getMovieWatchProviderList("ko-KR", "KR")).thenReturn(response());
		when(tmdbClient.getTvWatchProviderList("ko-KR", "KR")).thenReturn(response());

		assertThatThrownBy(service::synchronize)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("master is empty");
		verify(repository, never()).synchronizeProviderMaster(anyList());
	}

	private TmdbWatchProviderListRes response(TmdbWatchProviderListRes.Provider... providers) {
		return new TmdbWatchProviderListRes(List.of(providers));
	}

	private TmdbWatchProviderListRes.Provider provider(
		Long id,
		String name,
		String logoPath,
		Integer displayPriority
	) {
		return new TmdbWatchProviderListRes.Provider(id, name, logoPath, displayPriority);
	}
}
