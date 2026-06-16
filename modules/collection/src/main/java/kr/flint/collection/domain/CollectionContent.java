package kr.flint.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_collection_content",
			columnNames = {"collection_id", "content_id"}
		),
		@UniqueConstraint(
			name = "uk_collection_content_sort_order",
			columnNames = {"collection_id", "sort_order"}
		)
	}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class CollectionContent extends Base {

	@ManyToOne(targetEntity = Collection.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "collection_id", nullable = false)
	private Collection collection;

	@Column(name = "content_id", nullable = false)
	private Long contentId;

	@Column(nullable = false)
	private boolean isSpoiler;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String reason;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	public static CollectionContent create(
		Collection collection,
		Long contentId,
		boolean isSpoiler,
		String reason,
		int sortOrder
	) {
		return new CollectionContent(collection, contentId, isSpoiler, reason, sortOrder);
	}
}
