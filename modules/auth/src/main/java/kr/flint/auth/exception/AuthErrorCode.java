package kr.flint.auth.exception;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements AppError {

    DUPLICATE_IDENTITY(HttpStatus.CONFLICT, "AUTH.DUPLICATE_IDENTITY", "Duplicate Identity", "이미 연결된 소셜 계정입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH.INVALID_TOKEN", "Invalid Token", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH.EXPIRED_TOKEN", "Expired Token", "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH.REFRESH_TOKEN_NOT_FOUND", "Refresh Token Not Found", "리프레시 토큰을 찾을 수 없습니다."),
    SOCIAL_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH.SOCIAL_AUTH_FAILED", "Social Auth Failed", "소셜 인증에 실패했습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH.UNSUPPORTED_PROVIDER", "Unsupported Provider", "지원하지 않는 소셜 로그인 제공자입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;
    private final String detail;
}
