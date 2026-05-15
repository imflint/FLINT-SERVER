package kr.flint.collection.exception;

import org.springframework.http.HttpStatus;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CollectionErrorCode implements AppError {
    COLLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "COLLECTION.NOT_FOUND", "Collection Not Found", "컬렉션을 찾을 수 없습니다."),
    COLLECTION_FORBIDDEN(HttpStatus.FORBIDDEN, "COLLECTION.FORBIDDEN", "Collection Forbidden", "해당 컬렉션을 수정할 권한이 없습니다."),
    COLLECTION_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "COLLECTION_REPORT.NOT_FOUND", "Collection Report Not Found", "컬렉션 신고를 찾을 수 없습니다."),
    COLLECTION_REPORT_ALREADY_RESOLVED(HttpStatus.CONFLICT, "COLLECTION_REPORT.ALREADY_RESOLVED", "Collection Report Already Resolved", "이미 처리된 컬렉션 신고입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;
    private final String detail;
}
