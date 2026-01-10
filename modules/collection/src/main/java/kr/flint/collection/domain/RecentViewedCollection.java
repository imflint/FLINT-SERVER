package kr.flint.collection.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "collection_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RecentViewedCollection extends BaseTime {
	@Column(nullable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "collection_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Collection collection;

	@Column(nullable = false)
	private LocalDateTime viewedAt;

	public static RecentViewedCollection create(Long userId, Collection collection) {
		return new RecentViewedCollection(userId, collection, LocalDateTime.now());
	}

	public void updateViewedAt() {
		this.viewedAt = LocalDateTime.now();
	}

}
