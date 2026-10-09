package kr.flint.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, unique = true, length = 32)
	private GenreCode code;
	@Column(nullable = false, unique = true)
	private String name;

	public static Genre create(String name){
		return create(GenreCode.resolve(name));
	}

	public static Genre create(GenreCode code) {
		return new Genre(code, code.displayName());
	}
}
