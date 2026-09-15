package kr.flint.exploration.exception;

import org.springframework.http.HttpStatus;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExplorationErrorCode implements AppError {
	SNAPSHOT_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "EXPLORATION.SNAPSHOT_DISABLED", "Exploration Unavailable", "탐색 진행 상태 저장 기능이 아직 활성화되지 않았습니다."),
	INVALID_PROGRESS(HttpStatus.CONFLICT, "EXPLORATION.INVALID_PROGRESS", "Invalid Exploration Progress", "탐색 위치는 현재 노출된 다음 작품으로만 이동할 수 있습니다."),
	SESSION_NOT_COMPLETED(HttpStatus.CONFLICT, "EXPLORATION.SESSION_NOT_COMPLETED", "Exploration Session Not Completed", "현재 탐색 세션을 모두 확인한 뒤 다음 세션으로 이동할 수 있습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String title;
	private final String detail;
}
