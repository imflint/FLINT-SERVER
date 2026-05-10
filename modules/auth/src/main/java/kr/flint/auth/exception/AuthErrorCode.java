package kr.flint.auth.exception;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements AppError {

    // 인증 관련
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH.UNAUTHORIZED", "Unauthorized", "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH.INVALID_CREDENTIALS", "Invalid Credentials", "아이디 또는 비밀번호가 올바르지 않습니다."),

    // 토큰 관련
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH.INVALID_TOKEN", "Invalid Token", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH.EXPIRED_TOKEN", "Expired Token", "만료된 토큰입니다."),
    TOKEN_BLACKLISTED(HttpStatus.UNAUTHORIZED, "AUTH.TOKEN_BLACKLISTED", "Token Blacklisted", "로그아웃된 토큰입니다."),

    // Refresh Token 관련
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH.REFRESH_TOKEN_NOT_FOUND", "Refresh Token Not Found", "리프레시 토큰을 찾을 수 없습니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "AUTH.REFRESH_TOKEN_REUSED", "Token Reused", "토큰이 재사용되었습니다. 보안을 위해 다시 로그인해주세요."),
    REFRESH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "AUTH.REFRESH_TOKEN_REVOKED", "Token Revoked", "토큰이 무효화되었습니다."),
    CONCURRENT_REFRESH_REQUEST(HttpStatus.CONFLICT, "AUTH.CONCURRENT_REFRESH", "Concurrent Request", "토큰 갱신 요청이 이미 처리 중입니다."),

    // 소셜 로그인 관련
    DUPLICATE_IDENTITY(HttpStatus.CONFLICT, "AUTH.DUPLICATE_IDENTITY", "Duplicate Identity", "이미 연결된 소셜 계정입니다."),
    SOCIAL_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH.SOCIAL_AUTH_FAILED", "Social Auth Failed", "소셜 인증에 실패했습니다."),
    SOCIAL_AUTH_INVALID_CODE(HttpStatus.BAD_REQUEST, "AUTH.SOCIAL_AUTH_INVALID_CODE", "Invalid Authorization Code", "유효하지 않은 인가 코드입니다."),
    SOCIAL_AUTH_SERVER_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "AUTH.SOCIAL_AUTH_SERVER_ERROR", "Social Provider Error", "소셜 로그인 서버에 문제가 발생했습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH.UNSUPPORTED_PROVIDER", "Unsupported Provider", "지원하지 않는 소셜 로그인 제공자입니다."),
    UNSUPPORTED_SOCIAL_FLOW(HttpStatus.BAD_REQUEST, "AUTH.UNSUPPORTED_SOCIAL_FLOW", "Unsupported Social Flow", "지원하지 않는 소셜 로그인 방식입니다."),

    // 서버 에러
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH.INTERNAL_SERVER_ERROR", "Internal Server Error", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;
    private final String detail;
}
