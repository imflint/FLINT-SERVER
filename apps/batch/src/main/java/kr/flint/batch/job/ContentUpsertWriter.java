package kr.flint.batch.job;

import java.util.List;
import java.util.Objects;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import kr.flint.batch.repository.ContentBatchJdbcRepository;
import kr.flint.batch.repository.ContentBatchJdbcRepository.ContentIdentity;
import kr.flint.batch.repository.OttBatchJdbcRepository;
import kr.flint.content.dto.ContentUpsertCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentUpsertWriter implements ItemWriter<ContentSyncDraft> {

	private final ContentBatchJdbcRepository contentBatchJdbcRepository;
	private final OttBatchJdbcRepository ottBatchJdbcRepository;

	@Override
	public void write(Chunk<? extends ContentSyncDraft> chunk) throws Exception {
		List<ContentSyncDraft> drafts = chunk.getItems().stream()
			.filter(Objects::nonNull)
			.map(ContentSyncDraft.class::cast)
			.toList();
		List<ContentUpsertCommand> commands = drafts.stream()
			.map(ContentSyncDraft::content)
			.filter(Objects::nonNull)
			.toList();

		if (commands.isEmpty()) {
			return;
		}

		try {
			contentBatchJdbcRepository.classifyAll(commands);
			List<ContentUpsertCommand> persistCommands = drafts.stream()
				.filter(ContentSyncDraft::persistContent)
				.map(ContentSyncDraft::content)
				.filter(Objects::nonNull)
				.toList();
			contentBatchJdbcRepository.upsertClassified(persistCommands);
			var contentIds = contentBatchJdbcRepository.findContentIdsFor(persistCommands);
			var ottDrafts = drafts.stream()
				.filter(ContentSyncDraft::persistContent)
				.filter(draft -> draft.content() != null && draft.content().syncable() && draft.ott() != null)
				.map(draft -> {
					ContentIdentity identity = new ContentIdentity(
						draft.content().tmdbId(),
						draft.content().mediaType()
					);
					Long contentId = contentIds.get(identity);
					if (contentId == null) {
						throw new IllegalStateException("Content was not found after upsert: " + identity);
					}
					return draft.ott().toDraft(contentId);
				})
				.toList();
			ottBatchJdbcRepository.replaceProviders(ottDrafts);
		} catch (Exception e) {
			log.warn("content batch upsert failed count={} cause={}", commands.size(), e.toString());
			throw e;
		}
	}
}
