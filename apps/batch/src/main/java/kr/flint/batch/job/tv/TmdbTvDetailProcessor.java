package kr.flint.batch.job.tv;

import java.util.List;

import org.springframework.batch.item.ItemProcessor;

import feign.FeignException;
import kr.flint.batch.job.TmdbIdLine;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentUpsertCommand;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbTvDetailRes;
import kr.flint.infra.tmdb.dto.TmdbTvFullDetailRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class TmdbTvDetailProcessor implements ItemProcessor<TmdbIdLine, ContentUpsertCommand> {

	private static final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";
	private static final String LANG = "ko-KR";

	private final TmdbClient tmdbClient;

	@Override
	public ContentUpsertCommand process(TmdbIdLine line) {
		if (line == null || line.id() == null) {
			return null;
		}
		try {
			TmdbTvFullDetailRes detail = tmdbClient.getTvFullDetail(line.id(), LANG);
			String poster = resolvePoster(detail.posterPath());
			if (poster == null) {
				log.debug("tv {} has no poster, skip", line.id());
				return null;
			}

			List<String> genres = detail.genres() == null ? List.of() :
				detail.genres().stream().map(TmdbTvDetailRes.TmdbGenre::name).toList();

			String creator = detail.created_by() == null ? "Unknown" : detail.created_by().stream()
				.map(TmdbTvDetailRes.Creator::name)
				.findFirst()
				.orElse("Unknown");

			int year = parseYear(detail.firstAirDate());
			String title = detail.name() != null ? detail.name() : line.originalTitle();

			return ContentUpsertCommand.of(
				line.id(),
				MediaType.TV,
				title,
				year,
				creator,
				detail.overview(),
				poster,
				genres
			);
		} catch (FeignException.NotFound nf) {
			log.debug("tv {} not found, skip", line.id());
			return null;
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
