package kr.flint.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
public class Collection extends BaseTime {

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private String image;

	@Column(nullable = false)
	private boolean isPublic;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private int bookmarkCount;

	public static Collection create(String title, String description, String image, boolean isPublic, Long userId) {
		return Collection.builder()
			.title(title)
			.description(description)
			.image(image)
			.isPublic(isPublic)
			.userId(userId)
			.bookmarkCount(0)
			.build();
	}

    // TODO: 동시성 이슈 및 sync 체크
	public void increaseBookmarkCount() {
		this.bookmarkCount++;
	}

	public void decreaseBookmarkCount() {
		if (this.bookmarkCount > 0) {
			this.bookmarkCount--;
		}
	}


}
