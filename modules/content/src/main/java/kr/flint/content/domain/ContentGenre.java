package kr.flint.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContentGenre extends Base {
	@ManyToOne(targetEntity = Content.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "content_id", nullable = false)
	private Content content;

	@ManyToOne(targetEntity = Genre.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "genre_id", nullable = false)
	private Genre genre;

	public static ContentGenre create(Content content, Genre genre) {
		return new ContentGenre(content, genre);
	}
}
