package kr.flint.batch.job;

import kr.flint.batch.job.ott.TmdbOttSnapshot;
import kr.flint.content.dto.ContentUpsertCommand;

public record ContentSyncDraft(
	ContentUpsertCommand content,
	TmdbOttSnapshot ott,
	boolean persistContent
) {
	public static ContentSyncDraft classified(ContentUpsertCommand command) {
		return new ContentSyncDraft(command, null, true);
	}

	public static ContentSyncDraft synchronizedContent(
		ContentUpsertCommand command,
		TmdbOttSnapshot ott
	) {
		return new ContentSyncDraft(command, ott, true);
	}

	public ContentSyncDraft classificationOnly() {
		return new ContentSyncDraft(content, null, false);
	}
}
