package kr.flint.terms.exception;

import org.springframework.http.HttpStatus;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TermsErrorCode implements AppError {
	TERMS_NOT_FOUND(HttpStatus.NOT_FOUND, "TERMS.NOT_FOUND", "Terms Not Found", "약관을 찾을 수 없습니다."),
	REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERMS.REQUIRED_NOT_AGREED", "Required Terms Not Agreed", "필수 약관에 모두 동의해야 합니다."),
	INVALID_TERMS_AGREEMENT(HttpStatus.BAD_REQUEST, "TERMS.INVALID_AGREEMENT", "Invalid Terms Agreement", "유효하지 않은 약관 동의입니다."),
	NO_ACTIVE_REQUIRED_TERMS(HttpStatus.INTERNAL_SERVER_ERROR, "TERMS.NO_ACTIVE_REQUIRED", "No Active Required Terms", "활성 필수 약관이 없습니다."),
	FORBIDDEN_TERMS_ADMIN(HttpStatus.FORBIDDEN, "TERMS.ADMIN_FORBIDDEN", "Terms Admin Forbidden", "약관 관리 권한이 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String title;
	private final String detail;
}
