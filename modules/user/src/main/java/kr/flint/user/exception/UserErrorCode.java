package kr.flint.user.exception;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements AppError {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER.NOT_FOUND", "User Not Found", "사용자를 찾을 수 없습니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "USER.DUPLICATE_NICKNAME", "Duplicate Nickname", "이미 사용 중인 닉네임입니다."),
    ALREADY_WITHDRAWN(HttpStatus.BAD_REQUEST, "USER.ALREADY_WITHDRAWN", "Already Withdrawn", "이미 탈퇴한 사용자입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;
    private final String detail;
}
