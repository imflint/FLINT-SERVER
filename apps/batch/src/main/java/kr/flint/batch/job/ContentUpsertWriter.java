package kr.flint.batch.job;

import java.util.List;
import java.util.Objects;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import kr.flint.batch.service.TmdbContentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentUpsertWriter implements ItemWriter<ContentSyncDraft> {

	private final TmdbContentSyncService contentSyncService;

	@Override
	public void write(Chunk<? extends ContentSyncDraft> chunk) throws Exception {
		List<ContentSyncDraft> drafts = chunk.getItems().stream()
			.filter(Objects::nonNull)
			.map(ContentSyncDraft.class::cast)
			.toList();
		try {
			contentSyncService.synchronize(drafts);
		} catch (Exception e) {
			log.warn("content batch upsert failed count={} cause={}", drafts.size(), e.toString());
			throw e;
		}
	}
}
