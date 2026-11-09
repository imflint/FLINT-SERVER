package kr.flint.system.exception;

import org.springframework.http.HttpStatus;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemErrorCode implements AppError {
	APP_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM.APP_VERSION_NOT_FOUND", "App Version Not Found",
		"해당 플랫폼의 앱 버전 정책이 등록되지 않았습니다."),
	INVALID_APP_VERSION_FORMAT(HttpStatus.BAD_REQUEST, "SYSTEM.INVALID_APP_VERSION_FORMAT", "Invalid App Version Format",
		"최소 지원 버전은 x.y.z 형식이어야 합니다."),
	INVALID_STORE_PATH(HttpStatus.BAD_REQUEST, "SYSTEM.INVALID_STORE_PATH", "Invalid Store Path",
		"스토어 주소가 유효하지 않습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String title;
	private final String detail;
}
