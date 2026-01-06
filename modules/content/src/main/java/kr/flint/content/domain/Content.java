package kr.flint.content.domain;

import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

	@ElementCollection
	@CollectionTable(
		name = "content_genre",
		joinColumns = @JoinColumn(name = "content_id")
	)
	@Column(name = "genre", nullable = false)
	private List<String> genre;

	public Content(Long tmdbId, String title, int year, String author, String description, String poster,
		List<String> genre) {
		this.tmdbId = tmdbId;
		this.title = title;
		this.year = year;
		this.author = author;
		this.description = description;
		this.poster = poster;
		this.genre = genre;
	}
}
