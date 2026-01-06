package kr.flint.bookmark.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
			name = "uk_bookmark_collection_user",
			columnNames = {"user_id", "collection_id"}
		)
	}
)
public class CollectionBookmark extends Base {
	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long collectionId;

	public static CollectionBookmark create(Long userId, Long collectionId) {
		return new CollectionBookmark(userId, collectionId);
	}
}
