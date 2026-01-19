package kr.flint.api.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.api.domain.content.dto.GetPopularContentRes;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.Genre;
import kr.flint.content.exception.ContentErrorCode;
import kr.flint.content.exception.ContentException;
import kr.flint.content.service.ContentService;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbCommonRes;
import kr.flint.infra.tmdb.dto.TmdbGenreListRes;
import kr.flint.infra.tmdb.dto.TmdbMovieCreditRes;
import kr.flint.infra.tmdb.dto.TmdbTvDetailRes;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.SliceCursor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentCommandFacade {
	private final ContentService contentService;
	private final TmdbClient tmdbClient;
	private final OttCommandFacade ottCommandFacade;

	private static final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

	@Transactional
	public PaginationResponse<GetContentSearchRes> getContentSearchList(final String keyword, final int cursor, int size){
		log.info("keyword: {}", keyword);
		if (keyword != null && !keyword.isEmpty()) {
			List<GetContentSearchRes> searchRes = getTmdbContentList(keyword, cursor);
			return PaginationResponse.ofCursor(SliceCursor.of(searchRes, "0", "0"));
		}

		GetPopularContentRes popularContentList = getPopularContent(cursor);
		boolean hasNext = cursor < popularContentList.page();
		String nextCursor = String.valueOf(cursor + 1);

		return PaginationResponse.ofCursor(
			SliceCursor.of(popularContentList.contents(), String.valueOf(cursor), nextCursor)
		);
	}

	private GetPopularContentRes getPopularContent(int page) {
		try{
			log.info("실행 메서드: 인기");
			TmdbCommonRes tmdbList = tmdbClient.getPopularMovieList("ko-KR", page);
			List<GetContentSearchRes> popularList = tmdbList.contentList().stream()
				.map(result -> {
					Content existing = contentService.getContentByTmdbId(result.id());
					if (existing != null) {
						return GetContentSearchRes.of(
							existing.getTmdbId(),
							existing.getTitle(),
							existing.getAuthor(),
							existing.getPoster(),
							existing.getYear()
						);
					}

					String posterUrl = result.poster() == null
						? null
						: TMDB_IMAGE_BASE + result.poster();
					String author = extractMovieAuthor(result.id());
					List<Genre> tmdbGenreList = extractMovieGenreList(result.id()).stream()
						.map(name -> contentService.getGenre(name))
						.toList();
					log.info("날짜 1: ", result.firstAirDate());
					log.info("날짜 2: ", result.releaseDate());

					int year = extractYearFromDate(result.releaseDate());
					Content content = Content.create(
						result.id(),
						result.title(),
						year,
						author,
						result.overview(),
						posterUrl
					);

					Content savedContent = contentService.tmdbToDb(content, tmdbGenreList);
					ottCommandFacade.mapContentOtt(savedContent.getId(), result.id(), result.mediaType());

					return GetContentSearchRes.of(
						result.id(),
						result.title(),
						author,
						posterUrl,
						year
					);
				})
				.toList();

			return GetPopularContentRes.from(popularList, page);
		} catch (Exception e) {
			throw new ContentException(ContentErrorCode.TMDB_CONTENT_NOT_FOUND);
		}

	}


	@Transactional
	public List<GetContentSearchRes> getTmdbContentList(final String keyword, final int page) {
		log.info("실행 메서드 : 전체");

		TmdbCommonRes tmdbSearchRes = tmdbClient.getMultiList(keyword, "ko-KR", page);

		return tmdbSearchRes.contentList().stream()
			.filter(r -> "movie".equals(r.mediaType()) || "tv".equals(r.mediaType()))
			.map(result -> {

				Content existing = contentService.getContentByTmdbId(result.id());
				if (existing != null) {
					return GetContentSearchRes.of(
						existing.getTmdbId(),
						existing.getTitle(),
						existing.getAuthor(),
						existing.getPoster(),
						existing.getYear()
					);
				}
				String author;
				String posterUrl = result.poster() == null
					? null
					: TMDB_IMAGE_BASE + result.poster();
				List<String> tmdbGenreList;
				int year = extractYear(result.mediaType(), result.releaseDate(), result.firstAirDate());

				if ("movie".equals(result.mediaType())) {
					author = extractMovieAuthor(result.id());
					tmdbGenreList = extractMovieGenreList(result.id());
				} else {
					author = extractTvAuthor(result.id());
					tmdbGenreList = extractTvGenreList(result.id());
				}

				Content content = Content.create(
					result.id(),
					result.title(),
					year,
					author,
					result.overview(),
					posterUrl
				);

				List<Genre> genreList = tmdbGenreList.stream()
					.filter(genreName -> !contentService.checkGenre(genreName))
					.map(Genre::create)
					.toList();

				Content savedContent = contentService.tmdbToDb(content, genreList);
				ottCommandFacade.mapContentOtt(savedContent.getId(), result.id(), result.mediaType());

				return GetContentSearchRes.of(
					result.id(),
					result.title(),
					author,
					posterUrl,
					year
				);
			})
			.toList();
	}

	private String extractMovieAuthor(final Long id){
		TmdbMovieCreditRes tmdbMovieCreditRes = tmdbClient.getMovieCredit(id, "ko-KR");
		return tmdbMovieCreditRes.crew().stream()
			.filter(c -> "Director".equals(c.job()))
			.map(TmdbMovieCreditRes.Crew::name)
			.findFirst()
			.orElse("Unknown");
	}

	private String extractTvAuthor(final Long id){
		TmdbTvDetailRes tmdbTvDetailRes = tmdbClient.getTvDetail(id, "ko-KR");
		return tmdbTvDetailRes.created_by().stream()
			.map(TmdbTvDetailRes.Creator::name)
			.findFirst()
			.orElse("Unknown");
	}

	private List<String> extractMovieGenreList(final Long id){
		TmdbGenreListRes tmdbGenreListRes= tmdbClient.getMovieDetail(id, "ko_KR");
		return tmdbGenreListRes.genres()
			.stream()
			.map(TmdbGenreListRes.TmdbGenre::name)
			.toList();
	}


	private List<String> extractTvGenreList(final Long id){
		TmdbTvDetailRes tmdbGenreListRes= tmdbClient.getTvDetail(id, "ko_KR");
		return tmdbGenreListRes.genres()
			.stream()
			.map(TmdbTvDetailRes.TmdbGenre::name)
			.toList();
	}

	private int extractYear(String mediaType, String releaseDate, String firstAirDate) {
		log.info(mediaType + " " + releaseDate + " " + firstAirDate);
		String date = "movie".equals(mediaType) ? releaseDate : firstAirDate;
		return extractYearFromDate(date);
	}

	private int extractYearFromDate(String releaseDate) {
		log.info(releaseDate);
		if (releaseDate == null || releaseDate.isBlank() || releaseDate.length() < 4) return 0;
		return Integer.parseInt(releaseDate.substring(0, 4));
	}

}
