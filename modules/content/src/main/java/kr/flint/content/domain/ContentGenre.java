package kr.flint.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_content_genre",
			columnNames = {"content_id", "genre_id"}
		)
	},
	indexes = @Index(name = "idx_content_genre_genre_content", columnList = "genre_id, content_id")
)
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
