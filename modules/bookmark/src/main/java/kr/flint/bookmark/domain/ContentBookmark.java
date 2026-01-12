package kr.flint.bookmark.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.BaseTime;
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
			name = "uk_bookmark_user_content",
			columnNames = {"user_id", "content_id"}
		)
	}
)
public class ContentBookmark extends BaseTime {
	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long contentId;

	public static ContentBookmark create(Long userId, Long contentId) {
		return new ContentBookmark(userId, contentId);
	}
}
