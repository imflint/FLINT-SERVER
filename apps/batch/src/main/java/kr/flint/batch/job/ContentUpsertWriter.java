package kr.flint.batch.job;

import java.util.List;
import java.util.Objects;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import kr.flint.batch.repository.ContentBatchJdbcRepository;
import kr.flint.content.dto.ContentUpsertCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentUpsertWriter implements ItemWriter<ContentUpsertCommand> {

	private final ContentBatchJdbcRepository contentBatchJdbcRepository;

	@Override
	public void write(Chunk<? extends ContentUpsertCommand> chunk) throws Exception {
		List<ContentUpsertCommand> commands = chunk.getItems().stream()
			.filter(Objects::nonNull)
			.map(ContentUpsertCommand.class::cast)
			.toList();

		if (commands.isEmpty()) {
			return;
		}

		try {
			contentBatchJdbcRepository.upsertAll(commands);
		} catch (Exception e) {
			log.warn("content batch upsert failed count={} cause={}", commands.size(), e.toString());
			throw e;
		}
	}
}
