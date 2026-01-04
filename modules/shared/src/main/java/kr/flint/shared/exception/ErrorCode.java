package kr.flint.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements AppError {

    // 5xx
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON.INTERNAL_ERROR", "Internal Server Error", "서버 내부 오류가 발생했습니다."),

    // common 4xx
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON.BAD_REQUEST", "Bad Request", "잘못된 요청입니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON.INVALID_INPUT", "Invalid Input", "입력값이 올바르지 않습니다: %s"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON.MISSING_PARAMETER", "Missing Parameter", "필수 요청 파라미터가 누락되었습니다: %s"),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON.UNAUTHORIZED", "Unauthorized", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON.FORBIDDEN", "Forbidden", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON.NOT_FOUND", "Not Found", "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON.CONFLICT", "Conflict", "리소스 충돌이 발생했습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON.TOO_MANY_REQUESTS", "Too Many Requests", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;
    private final String detail;
}
