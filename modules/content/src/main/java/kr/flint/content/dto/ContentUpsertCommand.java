package kr.flint.content.dto;

import java.util.List;

import kr.flint.content.domain.MediaType;

public record ContentUpsertCommand(
	Long tmdbId,
	MediaType mediaType,
	String title,
	int year,
	String author,
	String description,
	String poster,
	List<String> genreNames
) {
	public static ContentUpsertCommand of(
		Long tmdbId,
		MediaType mediaType,
		String title,
		int year,
		String author,
		String description,
		String poster,
		List<String> genreNames
	) {
		return new ContentUpsertCommand(
			tmdbId,
			mediaType,
			title,
			year,
			author,
			description,
			poster,
			genreNames == null ? List.of() : genreNames
		);
	}
}
