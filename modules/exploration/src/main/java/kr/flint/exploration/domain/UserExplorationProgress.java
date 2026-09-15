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

	// 현재 세션에서 노출 가능한 작품을 모두 확인한 상태
	@Column(name = "completed", nullable = false)
	private boolean completed;

	@Column(name = "session_version", nullable = false)
	private int sessionVersion;

	@Column(name = "last_viewed_position", nullable = false)
	private int lastViewedPosition;

	public static UserExplorationProgress create(Long userId) {
		return new UserExplorationProgress(userId, null, false, 1, 0);
	}

	// 다음 세션으로 전진: 시작 경계를 다음 세트 시작으로 옮기고 완료 상태를 해제한다.
	public void advance(Long nextSessionCursor) {
		this.sessionCursor = nextSessionCursor;
		this.completed = false;
		this.sessionVersion++;
		this.lastViewedPosition = 0;
	}

	// 현재 세션에서 노출 가능한 작품을 모두 확인했음을 기록한다.
	public void markCompleted() {
		this.completed = true;
	}

	public void recordViewedPosition(int position) {
		if (position < lastViewedPosition) {
			throw new IllegalArgumentException("탐색 위치는 이전 위치보다 작을 수 없습니다.");
		}
		this.lastViewedPosition = position;
	}

	public boolean isCompleted() {
		return this.completed;
	}
}
