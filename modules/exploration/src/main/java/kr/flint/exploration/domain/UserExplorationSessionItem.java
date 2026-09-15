package kr.flint.exploration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
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
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
	name = "user_exploration_session_item",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_exploration_session_position",
		columnNames = {"user_id", "session_version", "position"}
	),
	indexes = @Index(
		name = "idx_exploration_session_lookup",
		columnList = "user_id, session_version, position"
	)
)
public class UserExplorationSessionItem extends BaseTime {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "session_version", nullable = false)
	private int sessionVersion;

	@Column(nullable = false)
	private int position;

	@Column(name = "content_id", nullable = false)
	private Long contentId;

	@Column(nullable = false)
	private String title;

	@Column
	private String poster;

	@Column(nullable = false)
	private int year;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "collection_id", nullable = false)
	private Long collectionId;

	public static UserExplorationSessionItem create(
		Long userId,
		int sessionVersion,
		int position,
		Long contentId,
		String title,
		String poster,
		int year,
		String description,
		Long collectionId
	) {
		return UserExplorationSessionItem.builder()
			.userId(userId)
			.sessionVersion(sessionVersion)
			.position(position)
			.contentId(contentId)
			.title(title)
			.poster(poster)
			.year(year)
			.description(description)
			.collectionId(collectionId)
			.build();
	}
}
