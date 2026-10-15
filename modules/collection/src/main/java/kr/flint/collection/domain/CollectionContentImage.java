package kr.flint.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kr.flint.shared.domain.Base;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
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

	public static final int MAX_IMAGE_KEY_LENGTH = 1024;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private CollectionContent collectionContent;

	@Column(nullable = false, length = MAX_IMAGE_KEY_LENGTH)
	private String imageKey;

	@Column(nullable = false)
	private int sortOrder;

	public static CollectionContentImage create(
		CollectionContent collectionContent,
		String imageKey,
		int sortOrder
	) {
		if (imageKey != null && imageKey.length() > MAX_IMAGE_KEY_LENGTH) {
			throw new GeneralException(ErrorCode.INVALID_INPUT, "커스텀 이미지 값은 1024자 이하여야 합니다.");
		}
		return new CollectionContentImage(collectionContent, imageKey, sortOrder);
	}
}
