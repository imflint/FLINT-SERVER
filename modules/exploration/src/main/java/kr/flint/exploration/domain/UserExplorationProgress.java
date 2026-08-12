package kr.flint.exploration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
	uniqueConstraints = @UniqueConstraint(
		name = "uk_exploration_progress_user",
		columnNames = "user_id"
	)
)
public class UserExplorationProgress extends BaseTime {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	// 현재 세션의 시작 경계(exclusive). null이면 첫 세션(맨 앞부터)
	@Column(name = "session_cursor")
	private Long sessionCursor;

	// 현재 세션을 끝까지 봤고, 그 시점에 다음 세트가 없어 End에 도달한 상태
	@Column(name = "completed", nullable = false)
	private boolean completed;

	public static UserExplorationProgress create(Long userId) {
		return new UserExplorationProgress(userId, null, false);
	}

	// 다음 세션으로 전진: 시작 경계를 다음 세트 시작으로 옮기고 End 상태를 해제한다.
	public void advance(Long nextSessionCursor) {
		this.sessionCursor = nextSessionCursor;
		this.completed = false;
	}

	// 현재 세션에서 End에 도달했음을 기록한다. (다음 세트가 아직 없음)
	public void markCompleted() {
		this.completed = true;
	}

	public boolean isCompleted() {
		return this.completed;
	}
}
