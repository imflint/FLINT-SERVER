package kr.flint.batch.job;

import org.springframework.batch.core.SkipListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TmdbBatchSkipListener implements SkipListener<Object, Object> {

	@Override
	public void onSkipInRead(Throwable t) {
		log.warn("[tmdb-skip] read", t);
	}

	@Override
	public void onSkipInProcess(Object item, Throwable t) {
		log.warn("[tmdb-skip] process item={} cause={}", item, t.toString());
	}

	@Override
	public void onSkipInWrite(Object item, Throwable t) {
		log.warn("[tmdb-skip] write item={} cause={}", item, t.toString());
	}
}
