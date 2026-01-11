package kr.flint.ott.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class OttProvider extends Base {
	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String logoUrl;

	@Column(nullable = false)
	private String url;
}
