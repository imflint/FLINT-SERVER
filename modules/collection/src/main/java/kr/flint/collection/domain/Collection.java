package kr.flint.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Collection extends BaseTime {

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String description;

	@Column(name = "collection_image", nullable = false)
	private String image;

	@Column(nullable = false)
	private boolean isPublic;

	public Collection(String title, String description, String image, boolean isPublic) {
		this.title = title;
		this.description = description;
		this.image = image;
		this.isPublic = isPublic;
	}

	public static Collection create(String title, String description, String image, boolean isPublic) {
		return new Collection(title, description, image, isPublic);
	}
}
