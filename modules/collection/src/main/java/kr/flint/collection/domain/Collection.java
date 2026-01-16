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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder(access = AccessLevel.PROTECTED)
public class Collection extends BaseTime {

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String description;

	@Column(name = "collection_image", nullable = false)
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

	public void increaseBookmarkCount() {
		this.bookmarkCount++;
	}

	public void decreaseBookmarkCount() {
		if (this.bookmarkCount > 0) {
			this.bookmarkCount--;
		}
	}

}
