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
    ALREADY_WITHDRAWN(HttpStatus.BAD_REQUEST, "USER.ALREADY_WITHDRAWN", "Already Withdrawn", "이미 탈퇴한 사용자입니다."),
    INVALID_REAL_NAME(HttpStatus.BAD_REQUEST, "USER.INVALID_REAL_NAME", "Invalid Real Name", "실명은 필수입니다."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "USER.INVALID_NICKNAME", "Invalid Nickname", "닉네임은 2자 이상 8자 이하이며 한글, 영문, 숫자만 사용할 수 있습니다."),
    KEYWORD_RECALC_NOT_READY(HttpStatus.BAD_REQUEST, "USER.KEYWORD_RECALC_NOT_READY", "Keyword Recalc Not Ready", "취향 키워드 재계산 가능 조건(신규 저장 작품 20개 누적)을 충족하지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;
    private final String detail;
}
