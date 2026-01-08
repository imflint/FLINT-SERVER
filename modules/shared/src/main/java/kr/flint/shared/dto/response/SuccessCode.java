package kr.flint.shared.dto.response;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
	/* 201 CREATED */
	SUCCESS_CREATE(HttpStatus.CREATED, "생성이 완료되었습니다"),

	/* 200 OK */
	SUCCESS_UPDATE(HttpStatus.OK, "업데이트가 완료되었습니다"),
	SUCCESS_DELETE(HttpStatus.OK, "삭제가 완료되었습니다"),
	SUCCESS_FETCH(HttpStatus.OK, "요청 데이터가 성공적으로 조회되었습니다");

	private final HttpStatus httpStatus;
	private final String message;
}
