package kr.flint.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Genre extends Base {
	@Column(nullable = false)
	private String name;

	@ManyToOne(targetEntity = Content.class, fetch = FetchType.LAZY)
	@JoinColumn(name = "content_id", nullable = false)
	private Content content;
}
