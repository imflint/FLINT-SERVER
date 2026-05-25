package kr.flint.api.domain.content.dto;

import java.util.List;

import org.springframework.util.StringUtils;

import kr.flint.content.domain.MediaType;

public record ContentSearchCondition(
	String keyword,
	List<String> genreNames,
	MediaType mediaType,
	int page,
	int size
) {
	public ContentSearchCondition {
		genreNames = genreNames == null ? List.of() : List.copyOf(genreNames);
	}

	public static ContentSearchCondition of(
		String keyword,
		List<String> genreNames,
		MediaType mediaType,
		int page,
		int size
	) {
		return new ContentSearchCondition(keyword, genreNames, mediaType, page, size);
	}

	public boolean hasKeyword() {
		return StringUtils.hasText(keyword);
	}

	public boolean hasGenres() {
		return !genreNames.isEmpty();
	}

	public boolean usesFullTextSearch() {
		return hasKeyword() && keyword.trim().length() > 1;
	}

	public int queryLimit() {
		return size + 1;
	}

	public long offset() {
		return (long) (page - 1) * size;
	}
}
