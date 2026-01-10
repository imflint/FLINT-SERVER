package kr.flint.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class Content extends BaseTime {

	@Column(nullable = false, unique = true)
	private Long tmdbId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private int year;

	@Column(nullable = false)
	private String author;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private String poster;

	@Column(nullable = false)
	private int bookmarkCount;

	public static Content create(Long tmdbId, String title, int year, String author, String description, String poster) {
		return Content.builder()
			.tmdbId(tmdbId)
			.title(title)
			.year(year)
			.author(author)
			.description(description)
			.poster(poster)
			.bookmarkCount(0)
			.build();
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
