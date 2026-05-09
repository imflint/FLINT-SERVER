package kr.flint.batch.job;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import kr.flint.content.dto.ContentUpsertCommand;
import kr.flint.content.service.ContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentUpsertWriter implements ItemWriter<ContentUpsertCommand> {

	private final ContentService contentService;

	@Override
	public void write(Chunk<? extends ContentUpsertCommand> chunk) {
		for (ContentUpsertCommand cmd : chunk) {
			if (cmd == null) {
				continue;
			}
			try {
				contentService.upsertWithGenres(cmd);
			} catch (Exception e) {
				log.warn("upsert failed tmdbId={} mediaType={} cause={}", cmd.tmdbId(), cmd.mediaType(), e.toString());
			}
		}
	}
}
