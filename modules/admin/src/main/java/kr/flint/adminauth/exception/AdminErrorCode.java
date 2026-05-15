package kr.flint.adminauth.exception;

import org.springframework.http.HttpStatus;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminErrorCode implements AppError {

    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN.NOT_FOUND", "Admin Not Found", "관리자 계정을 찾을 수 없습니다."),
    ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "ADMIN.FORBIDDEN", "Admin Forbidden", "관리자 권한이 없습니다."),
    DUPLICATE_ADMIN_USERNAME(HttpStatus.CONFLICT, "ADMIN.DUPLICATE_USERNAME", "Duplicate Admin Username", "이미 사용 중인 관리자 로그인 ID입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;
    private final String detail;
}
