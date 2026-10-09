package kr.flint.api.domain.content.dto;

import org.springframework.util.StringUtils;
import kr.flint.api.common.query.ContentSearchKeyword;
import kr.flint.content.domain.GenreCode;

import kr.flint.content.domain.MediaType;

public record ContentSearchCondition(
	String keyword,
	String genreName,
	MediaType mediaType,
	ContentSearchCursor cursor,
	int size
) {
	public ContentSearchCondition {
		ContentSearchKeyword prepared = ContentSearchKeyword.ofNullable(keyword);
		keyword = prepared == null ? null : prepared.raw();
		genreName = StringUtils.hasText(genreName) ? genreName.trim() : null;
	}

	public static ContentSearchCondition of(
		String keyword,
		String genreName,
		MediaType mediaType,
		ContentSearchCursor cursor,
		int size
	) {
		return new ContentSearchCondition(keyword, genreName, mediaType, cursor, size);
	}

	public boolean hasKeyword() {
		return StringUtils.hasText(keyword);
	}

	public boolean hasGenre() {
		return genreName != null;
	}

	public GenreCode genreCode() {
		return GenreCode.find(genreName).orElse(null);
	}

	public int queryLimit() {
		return size + 1;
	}
}
