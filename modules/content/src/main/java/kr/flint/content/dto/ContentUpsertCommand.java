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
	String errorMessage,
	List<Long> tmdbGenreIds
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
			null, List.of()
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
			null, List.of()
		);
	}

	public static ContentUpsertCommand classified(
		Long tmdbId,
		MediaType mediaType,
		ContentCatalogStatus status,
		String errorMessage
	) {
		return new ContentUpsertCommand(
			tmdbId, mediaType, null, null, 0, null, null, null, List.of(), status, errorMessage, List.of()
		);
	}

	public boolean syncable() {
		return catalogStatus == ContentCatalogStatus.SYNCED;
	}

    public ContentUpsertCommand preservingTitles(String titleKo, String titleEn) {
        return new ContentUpsertCommand(tmdbId, mediaType, titleKo, titleEn, year, author, description,
            poster, genreNames, catalogStatus, "Title update conflicts with another content; existing titles retained", tmdbGenreIds);
    }

    public ContentUpsertCommand duplicateTitle(String reason) {
        return new ContentUpsertCommand(tmdbId, mediaType, titleKo, titleEn, year, author, description,
            poster, genreNames, ContentCatalogStatus.DUPLICATE_TITLE, reason, tmdbGenreIds);
    }

    public ContentUpsertCommand withTmdbGenreIds(List<Long> ids) {
        return new ContentUpsertCommand(tmdbId, mediaType, titleKo, titleEn, year, author, description,
            poster, List.of(), catalogStatus, errorMessage, ids == null ? List.of() : ids);
    }

    public ContentUpsertCommand retry(String reason) {
        return new ContentUpsertCommand(tmdbId, mediaType, titleKo, titleEn, year, author, description,
            poster, genreNames, ContentCatalogStatus.RETRY, reason, tmdbGenreIds);
    }
}
