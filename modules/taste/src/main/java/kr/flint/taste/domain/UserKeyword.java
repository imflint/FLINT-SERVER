package kr.flint.taste.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
    name = "user_keywords",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_keyword",
            columnNames = {"user_id", "keyword_id"}
        )
    }
)
public class UserKeyword extends BaseTime {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "keyword_id", nullable = false)
    private Long keywordId;

    @Column(nullable = false)
    private Integer percentage;

	@Column(name = "ranking", nullable = false)
	private int ranking;

    public static UserKeyword create(Long userId, Long keywordId, Integer percentage, int rank) {
        return UserKeyword.builder()
            .userId(userId)
            .keywordId(keywordId)
            .percentage(percentage)
			.ranking(rank)
            .build();
    }

    public void updatePercentage(Integer percentage) {
        this.percentage = percentage;
    }
}
