package kr.flint.batch.job.ott;

import java.util.List;
import java.util.Objects;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import kr.flint.batch.repository.OttBatchJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OttSyncWriter implements ItemWriter<OttSyncDraft> {

	private final OttBatchJdbcRepository ottBatchJdbcRepository;

	@Override
	public void write(Chunk<? extends OttSyncDraft> chunk) throws Exception {
		List<OttSyncDraft> drafts = chunk.getItems().stream()
			.filter(Objects::nonNull)
			.map(OttSyncDraft.class::cast)
			.toList();

		if (drafts.isEmpty()) {
			return;
		}

		try {
			ottBatchJdbcRepository.linkProviders(drafts);
		} catch (Exception e) {
			log.warn("ott batch link failed count={} cause={}", drafts.size(), e.toString());
			throw e;
		}
	}
}
