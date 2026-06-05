package kr.flint.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collection_content_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionContentImage extends Base {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private CollectionContent collectionContent;

	@Column(nullable = false)
	private String imageKey;

	@Column(nullable = false)
	private int sortOrder;

	public static CollectionContentImage create(
		CollectionContent collectionContent,
		String imageKey,
		int sortOrder
	) {
		return new CollectionContentImage(collectionContent, imageKey, sortOrder);
	}
}
