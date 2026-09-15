package kr.flint.taste.exception;

import org.springframework.http.HttpStatus;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TasteErrorCode implements AppError {
	KEYWORD_NOT_FOUND(HttpStatus.NOT_FOUND, "KEYWORD.NOT_FOUND", "Keyword Not Found", "키워드를 찾을 수 없습니다."),
	INVALID_ANALYSIS(HttpStatus.BAD_GATEWAY, "TASTE.INVALID_ANALYSIS", "Invalid Taste Analysis", "취향 분석 결과가 유효하지 않습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String title;
	private final String detail;
}
