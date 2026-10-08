package kr.flint.api.domain.content.dto;

import org.springframework.util.StringUtils;

import kr.flint.content.domain.MediaType;

public record ContentSearchCondition(
	String keyword,
	String genreName,
	MediaType mediaType,
	ContentSearchCursor cursor,
	int size
) {
	public ContentSearchCondition {
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

	public boolean usesFullTextSearch() {
		return hasKeyword() && keyword.trim().length() > 1;
	}

	public int queryLimit() {
		return size + 1;
	}
}
