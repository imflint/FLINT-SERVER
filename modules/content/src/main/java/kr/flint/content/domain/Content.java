package kr.flint.content.domain;

import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
}
