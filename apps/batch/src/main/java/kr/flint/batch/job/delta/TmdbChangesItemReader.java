package kr.flint.batch.job.delta;

import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.batch.item.ItemReader;

import kr.flint.batch.job.TmdbIdLine;
import kr.flint.content.domain.MediaType;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.infra.tmdb.dto.TmdbChangesRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// /3/movie/changes 또는 /3/tv/changes 를 페이지 단위로 순차 호출하며 변경된 ID 스트림을 만든다.
@RequiredArgsConstructor
@Slf4j
public class TmdbChangesItemReader implements ItemReader<TmdbIdLine> {

	private final TmdbClient tmdbClient;
	private final MediaType mediaType;
	private final String startDate;
	private final String endDate;

	private final Deque<TmdbIdLine> buffer = new ArrayDeque<>();
	private int page = 0;
	private int totalPages = 1;
	private boolean exhausted = false;

	@Override
	public TmdbIdLine read() {
		while (buffer.isEmpty() && !exhausted) {
			page++;
			if (page > totalPages) {
				exhausted = true;
				return null;
			}
			TmdbChangesRes res = mediaType == MediaType.TV
				? tmdbClient.getTvChanges(startDate, endDate, page)
				: tmdbClient.getMovieChanges(startDate, endDate, page);
			totalPages = Math.max(totalPages, res.totalPages());
			if (res.results() != null) {
				for (TmdbChangesRes.Result r : res.results()) {
					if (r.id() != null) {
						buffer.add(new TmdbIdLine(r.id(), null, null, r.adult()));
					}
				}
			}
		}
		return buffer.pollFirst();
	}
}
