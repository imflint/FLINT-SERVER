package kr.flint.content.dto;

import java.util.List;

import kr.flint.content.domain.MediaType;

public record ContentUpsertCommand(
	Long tmdbId,
	MediaType mediaType,
	String titleKo,
	String titleEn,
	int year,
	String author,
	String description,
	String poster,
	List<String> genreNames,
	ContentCatalogStatus catalogStatus,
	String errorMessage
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
			null,
			year,
			author,
			description,
			poster,
			genreNames == null ? List.of() : genreNames,
			ContentCatalogStatus.SYNCED,
			null
		);
	}

	public static ContentUpsertCommand localized(
		Long tmdbId,
		MediaType mediaType,
		String titleKo,
		String titleEn,
		int year,
		String author,
		String description,
		String poster,
		List<String> genreNames
	) {
		return new ContentUpsertCommand(
			tmdbId,
			mediaType,
			titleKo,
			titleEn,
			year,
			author,
			description,
			poster,
			genreNames == null ? List.of() : genreNames,
			ContentCatalogStatus.SYNCED,
			null
		);
	}

	public static ContentUpsertCommand classified(
		Long tmdbId,
		MediaType mediaType,
		ContentCatalogStatus status,
		String errorMessage
	) {
		return new ContentUpsertCommand(
			tmdbId, mediaType, null, null, 0, null, null, null, List.of(), status, errorMessage
		);
	}

	public boolean syncable() {
		return catalogStatus == ContentCatalogStatus.SYNCED;
	}
}
