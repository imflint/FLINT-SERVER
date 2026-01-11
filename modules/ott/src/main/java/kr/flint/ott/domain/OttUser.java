package kr.flint.ott.domain;

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
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_ott_user",
			columnNames = {"ott_provider_id", "user_id"}
		)
	}
)
public class OttUser extends Base {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(nullable = false, name = "ott_provider_id")
	private OttProvider ottProvider;

	@Column(nullable = false)
	private Long userId;

	public static OttUser create(OttProvider ottProvider, Long userId){
		return new OttUser(ottProvider, userId);
	}
}
