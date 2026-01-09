package kr.flint.api.storage.exception;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StorageErrorCode implements AppError {

    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "STORAGE.INVALID_EXTENSION", "Invalid File Extension", "허용되지 않는 파일 확장자입니다."),
    INVALID_PATH_TYPE(HttpStatus.BAD_REQUEST, "STORAGE.INVALID_PATH_TYPE", "Invalid Path Type", "잘못된 저장 경로 타입입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;
    private final String detail;
}
