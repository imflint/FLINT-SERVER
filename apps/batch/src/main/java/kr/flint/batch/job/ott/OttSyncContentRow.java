package kr.flint.batch.job.ott;

import kr.flint.content.domain.MediaType;

public record OttSyncContentRow(
	Long contentId,
	Long tmdbId,
	MediaType mediaType
) {
}
