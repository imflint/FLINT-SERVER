package kr.flint.batch.job.movie;

import java.util.List;

import org.springframework.batch.item.ItemProcessor;

import feign.FeignException;
import kr.flint.batch.job.TmdbIdLine;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentUpsertCommand;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbGenreListRes;
import kr.flint.infra.tmdb.dto.TmdbMovieCreditRes;
import kr.flint.infra.tmdb.dto.TmdbMovieDetailRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// AsyncItemProcessor의 delegate. 영속성 컨텍스트 진입 금지 — TMDB API만 호출하여 ContentUpsertCommand 반환.
@RequiredArgsConstructor
@Slf4j
public class TmdbMovieDetailProcessor implements ItemProcessor<TmdbIdLine, ContentUpsertCommand> {

	private static final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";
	private static final String LANG = "ko-KR";

	private final TmdbClient tmdbClient;

	@Override
	public ContentUpsertCommand process(TmdbIdLine line) {
		if (line == null || line.id() == null) {
			return null;
		}
		try {
			TmdbMovieDetailRes detail = tmdbClient.getMovieFullDetail(line.id(), LANG);
			String poster = resolvePoster(detail.posterPath());
			if (poster == null) {
				log.debug("movie {} has no poster, skip", line.id());
				return null;
			}

			TmdbMovieCreditRes credit = tmdbClient.getMovieCredit(line.id(), LANG);

			List<String> genres = detail.genres() == null ? List.of() :
				detail.genres().stream().map(TmdbGenreListRes.TmdbGenre::name).toList();

			String director = credit.crew() == null ? "Unknown" : credit.crew().stream()
				.filter(c -> "Director".equals(c.job()))
				.map(TmdbMovieCreditRes.Crew::name)
				.findFirst()
				.orElse("Unknown");

			int year = parseYear(detail.releaseDate());
			String title = detail.title() != null ? detail.title() : line.originalTitle();

			return ContentUpsertCommand.of(
				line.id(),
				MediaType.MOVIE,
				title,
				year,
				director,
				detail.overview(),
				poster,
				genres
			);
		} catch (FeignException.NotFound nf) {
			log.debug("movie {} not found, skip", line.id());
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
