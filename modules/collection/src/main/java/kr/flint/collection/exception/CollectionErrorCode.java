package kr.flint.collection.exception;

import org.springframework.http.HttpStatus;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CollectionErrorCode implements AppError {
	COLLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "COLLECTION.NOT_FOUND", "Collection Not Found", "컬렉션을 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String title;
	private final String detail;
}
