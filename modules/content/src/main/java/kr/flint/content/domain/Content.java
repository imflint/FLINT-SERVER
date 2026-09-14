package kr.flint.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
	uniqueConstraints = @UniqueConstraint(
		name = "uk_content_tmdb",
		columnNames = {"tmdb_id", "media_type"}
	),
	indexes = {
		@Index(name = "idx_content_popular", columnList = "bookmark_count DESC, id DESC"),
		@Index(name = "idx_content_media_popular", columnList = "media_type, bookmark_count DESC, id DESC")
	}
)
public class Content extends BaseTime {

	@Column(name = "tmdb_id", nullable = false)
	private Long tmdbId;

	@Enumerated(EnumType.STRING)
	@Column(name = "media_type", nullable = false, length = 16)
	private MediaType mediaType;

	@Column(nullable = true)
	private String title;

	@Column(name = "title_ko")
	private String titleKo;

	@Column(name = "title_en")
	private String titleEn;

	@Column(name = "normalized_title_ko")
	private String normalizedTitleKo;

	@Column(name = "normalized_title_en")
	private String normalizedTitleEn;

	@Column(name = "search_title", columnDefinition = "TEXT")
	private String searchTitle;

	@Column(nullable = true)
	private int year;

	@Column(nullable = true)
	private String author;

	@Lob
	@Column(columnDefinition = "TEXT", nullable = true)
	private String description;

	@Column(nullable = true)
	private String poster;

	@Column(nullable = true)
	private int bookmarkCount;

	public static Content create(
		Long tmdbId,
		MediaType mediaType,
		String title,
		int year,
		String author,
		String description,
		String poster
	) {
		return createLocalized(tmdbId, mediaType, title, null, year, author, description, poster);
	}

	public static Content createLocalized(
		Long tmdbId,
		MediaType mediaType,
		String titleKo,
		String titleEn,
		int year,
		String author,
		String description,
		String poster
	) {
		return Content.builder()
			.tmdbId(tmdbId)
			.mediaType(mediaType)
			.year(year)
			.author(author)
			.description(description)
			.poster(poster)
			.bookmarkCount(0)
			.build()
			.applyLocalizedTitles(titleKo, titleEn);
	}

	public void updateMetadata(String title, int year, String author, String description, String poster) {
		applyLocalizedTitles(title, this.titleEn);
		this.year = year;
		this.author = author;
		this.description = description;
		this.poster = poster;
	}

	public void updateLocalizedMetadata(
		String titleKo,
		String titleEn,
		int year,
		String author,
		String description,
		String poster
	) {
		applyLocalizedTitles(titleKo, titleEn);
		this.year = year;
		this.author = author;
		this.description = description;
		this.poster = poster;
	}

	private Content applyLocalizedTitles(String titleKo, String titleEn) {
		this.titleKo = hasText(titleKo) ? titleKo.trim() : null;
		this.titleEn = hasText(titleEn) ? titleEn.trim() : null;
		this.title = ContentTitleNormalizer.displayTitle(this.titleKo, this.titleEn);
		this.normalizedTitleKo = ContentTitleNormalizer.normalizeNullable(this.titleKo);
		this.normalizedTitleEn = ContentTitleNormalizer.normalizeNullable(this.titleEn);
		this.searchTitle = ContentTitleNormalizer.buildSearchTitle(this.titleKo, this.titleEn);
		return this;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public void increaseBookmarkCount() {
		this.bookmarkCount++;
	}

	public void decreaseBookmarkCount() {
		if (this.bookmarkCount > 0) {
			this.bookmarkCount--;
		}
	}
}
