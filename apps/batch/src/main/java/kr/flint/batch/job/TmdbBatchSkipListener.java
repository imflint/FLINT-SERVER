package kr.flint.batch.job;

import org.springframework.batch.core.SkipListener;
import kr.flint.batch.service.TmdbApiException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TmdbBatchSkipListener implements SkipListener<Object, Object> {

	@Override
	public void onSkipInRead(Throwable t) {
		log.warn("[tmdb-skip] read cause={}", TmdbApiException.safeMessage(t));
	}

	@Override
	public void onSkipInProcess(Object item, Throwable t) {
		log.warn("[tmdb-skip] process cause={}", TmdbApiException.safeMessage(t));
	}

	@Override
	public void onSkipInWrite(Object item, Throwable t) {
		log.warn("[tmdb-skip] write cause={}", TmdbApiException.safeMessage(t));
	}
}
