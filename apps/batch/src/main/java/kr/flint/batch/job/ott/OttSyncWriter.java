package kr.flint.batch.job.ott;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import kr.flint.ott.service.OttSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OttSyncWriter implements ItemWriter<OttSyncDraft> {

	private final OttSyncService ottSyncService;

	@Override
	public void write(Chunk<? extends OttSyncDraft> chunk) {
		for (OttSyncDraft draft : chunk) {
			if (draft == null) {
				continue;
			}
			try {
				ottSyncService.linkProviders(draft.contentId(), draft.providerNames());
			} catch (Exception e) {
				log.warn("ott link failed contentId={} cause={}", draft.contentId(), e.toString());
			}
		}
	}
}
