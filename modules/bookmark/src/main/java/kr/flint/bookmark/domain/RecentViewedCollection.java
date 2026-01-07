package kr.flint.bookmark.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RecentViewedCollection extends BaseTime {
	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long collectionId;

	public static RecentViewedCollection create(Long userId, Long collectionId) {
		return new RecentViewedCollection(userId, collectionId);
	}

}
