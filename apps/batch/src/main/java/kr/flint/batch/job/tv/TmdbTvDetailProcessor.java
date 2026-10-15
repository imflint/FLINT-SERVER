package kr.flint.batch.job.tv;

import java.util.List;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.util.StringUtils;

import kr.flint.batch.service.TmdbApiException;
import kr.flint.batch.service.TmdbApiExecutor;
import kr.flint.batch.job.TmdbIdLine;
import kr.flint.batch.job.ContentSyncDraft;
import kr.flint.batch.job.ott.TmdbOttSnapshot;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentUpsertCommand;
import kr.flint.content.dto.ContentCatalogStatus;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbTvDetailRes;
import kr.flint.infra.tmdb.dto.TmdbTvFullDetailRes;
import kr.flint.batch.service.TmdbLocalizedTitleService;
import kr.flint.batch.service.TmdbLocalizedTitleService.LocalizedTitles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class TmdbTvDetailProcessor implements ItemProcessor<TmdbIdLine, ContentSyncDraft> {

	private static final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";
	private static final String LANG = "ko-KR";
	private static final String APPEND_TO_RESPONSE = "translations,credits,watch/providers";

	private final TmdbClient tmdbClient;
	private final TmdbLocalizedTitleService localizedTitleService;
	private final TmdbApiExecutor apiExecutor;

	@Override
	public ContentSyncDraft process(TmdbIdLine line) {
		if (line == null || line.id() == null) {
			return null;
		}
		try {
			TmdbTvFullDetailRes detail = apiExecutor.execute("tvDetail:" + line.id(), () -> {
				TmdbTvFullDetailRes response = tmdbClient.getTvFullDetail(line.id(), LANG, APPEND_TO_RESPONSE);
				if (response == null || !line.id().equals(response.id()) || response.genres() == null
					|| response.genres().stream().anyMatch(genre -> genre == null || genre.id() == null)
					|| response.translations() == null || response.translations().translations() == null
					|| response.translations().translations().stream().anyMatch(translation ->
						translation == null || translation.language() == null || translation.data() == null)
					|| response.credits() == null || response.credits().crew() == null
					|| response.credits().crew().stream().anyMatch(crew -> crew == null)
					|| response.created_by() == null || response.created_by().stream().anyMatch(creator -> creator == null)) {
					throw TmdbApiException.invalidResponse("tvDetail:" + line.id());
				}
				TmdbOttSnapshot.from(response.watchProviders());
				return response;
			});
			String poster = resolvePoster(detail.posterPath());
			List<Long> genres = detail.genres() == null ? List.of() :
				detail.genres().stream().map(g -> g.id() == null ? null : g.id().longValue()).toList();

			String creator = detail.created_by() == null ? null : detail.created_by().stream()
				.map(TmdbTvDetailRes.Creator::name)
				.filter(StringUtils::hasText)
				.findFirst()
				.orElse(null);

			int year = parseYear(detail.firstAirDate());
			LocalizedTitles titles = localizedTitleService.select(
				detail.originalLanguage(),
				detail.originalName(),
				detail.translations()
			);
			if (!titles.eligible()) {
				log.debug("tv {} has no Korean or English title, skip", line.id());
				return ContentSyncDraft.classified(ContentUpsertCommand.classified(
					line.id(), MediaType.TV, ContentCatalogStatus.INELIGIBLE_LANGUAGE, null
				));
			}

			ContentUpsertCommand command = ContentUpsertCommand.localized(
				line.id(),
				MediaType.TV,
				titles.titleKo(),
				titles.titleEn(),
				year,
				creator,
				detail.overview(),
				poster,
				List.of()
			).withTmdbGenreIds(genres);
			return ContentSyncDraft.synchronizedContent(command, TmdbOttSnapshot.from(detail.watchProviders()));
		} catch (TmdbApiException exception) {
			if (!exception.notFound() && !exception.retryable()) throw exception;
			return ContentSyncDraft.classified(ContentUpsertCommand.classified(
				line.id(), MediaType.TV,
				exception.notFound() ? ContentCatalogStatus.NOT_FOUND : ContentCatalogStatus.RETRY,
				TmdbApiException.safeMessage(exception)
			));
		}
	}

	private String resolvePoster(String posterPath) {
		if (posterPath == null || posterPath.isBlank()) {
			return null;
		}
		return TMDB_IMAGE_BASE + posterPath;
	}

	private int parseYear(String date) {
		if (date == null || date.length() < 4) {
			return 0;
		}
		try {
			return Integer.parseInt(date.substring(0, 4));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
