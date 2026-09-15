package kr.flint.batch.job.tv;

import java.util.List;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.util.StringUtils;

import feign.FeignException;
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

	@Override
	public ContentSyncDraft process(TmdbIdLine line) {
		if (line == null || line.id() == null) {
			return null;
		}
		try {
			TmdbTvFullDetailRes detail = tmdbClient.getTvFullDetail(line.id(), LANG, APPEND_TO_RESPONSE);
			String poster = resolvePoster(detail.posterPath());
			List<String> genres = detail.genres() == null ? List.of() :
				detail.genres().stream().map(TmdbTvDetailRes.TmdbGenre::name).toList();

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
				genres
			);
			return ContentSyncDraft.synchronizedContent(command, TmdbOttSnapshot.from(detail.watchProviders()));
		} catch (FeignException.NotFound nf) {
			log.debug("tv {} not found, skip", line.id());
			return ContentSyncDraft.classified(ContentUpsertCommand.classified(
				line.id(), MediaType.TV, ContentCatalogStatus.NOT_FOUND, nf.getMessage()
			));
		} catch (FeignException exception) {
			return ContentSyncDraft.classified(ContentUpsertCommand.classified(
				line.id(), MediaType.TV, ContentCatalogStatus.RETRY, exception.getMessage()
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
