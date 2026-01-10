package kr.flint.infra.tmdb.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
	name = "tmdbClient",
	url = "${tmdb.base-url}",
	configuration = TmdbFeignConfig.class
)
public interface TmdbClient {
	//@GetMapping("/search/movie")


	//@GetMapping("/movie/{movieId}")

}
