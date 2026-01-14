package kr.flint.infra.tmdb.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import kr.flint.infra.tmdb.config.TmdbFeignConfig;
import kr.flint.infra.tmdb.dto.TmdbCommonRes;
import kr.flint.infra.tmdb.dto.TmdbContentRes;
import kr.flint.infra.tmdb.dto.TmdbGenreListRes;
import kr.flint.infra.tmdb.dto.TmdbMovieCreditRes;
import kr.flint.infra.tmdb.dto.TmdbOttRes;
import kr.flint.infra.tmdb.dto.TmdbTvDetailRes;

@FeignClient(
	name = "tmdbClient",
	url = "https://api.themoviedb.org",
	configuration = TmdbFeignConfig.class
)
public interface TmdbClient {
	@GetMapping("/3/movie/popular")
	TmdbCommonRes getPopularMovieList(
		@RequestParam(value = "language", defaultValue = "ko-KR") String language,
		@RequestParam(value = "page", defaultValue = "1") int page
	);

	@GetMapping("/3/search/multi")
	TmdbCommonRes getMultiList(
		@RequestParam("query") String query,
		@RequestParam(value = "language", defaultValue = "ko-KR") String language,
		@RequestParam(value = "page", defaultValue = "1") int page
	);

	@GetMapping("/3/movie/{id}/credits")
	TmdbMovieCreditRes getMovieCredit(
		@PathVariable("id") Long id,
		@RequestParam(value = "language", defaultValue = "ko-KR") String language
	);

	@GetMapping("/3/tv/{id}")
	TmdbTvDetailRes getTvDetail(
		@PathVariable("id") Long id,
		@RequestParam(value = "language", defaultValue = "ko-KR") String language
	);

	@GetMapping("/3/movie/{id}")
	TmdbGenreListRes getMovieDetail(
		@PathVariable("id") Long id,
		@RequestParam(value = "language", defaultValue = "ko-KR") String language
	);

	@GetMapping("/3/movie/{id}/watch/providers")
	TmdbOttRes getMovieWatchProviders(@PathVariable("id") Long id);

	@GetMapping("/3/tv/{id}/watch/providers")
	TmdbOttRes getTvWatchProviders(@PathVariable("id") Long id);

}
