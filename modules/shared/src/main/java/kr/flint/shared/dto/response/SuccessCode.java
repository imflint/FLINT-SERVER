package kr.flint.shared.dto.response;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
	/* 201 CREATED */
	SUCCESS_CREATE(HttpStatus.CREATED, "생성이 완료되었습니다"),
	SUCCESS_SIGNUP(HttpStatus.CREATED, "회원가입이 완료되었습니다"),

	/* 200 OK */
	SUCCESS_UPDATE(HttpStatus.OK, "업데이트가 완료되었습니다"),
	SUCCESS_DELETE(HttpStatus.OK, "삭제가 완료되었습니다"),
	SUCCESS_FETCH(HttpStatus.OK, "요청 데이터가 성공적으로 조회되었습니다"),
	SUCCESS_LOGIN(HttpStatus.OK, "로그인이 완료되었습니다"),
	SUCCESS_TOKEN_REFRESH(HttpStatus.OK, "토큰이 갱신되었습니다"),
	SUCCESS_NICKNAME_CHECK(HttpStatus.OK, "닉네임 확인이 완료되었습니다"),
	SUCCESS_KEYWORDS_FETCH(HttpStatus.OK, "취향 키워드 조회가 완료되었습니다"),
	SUCCESS_COLLECTIONS_FETCH(HttpStatus.OK, "컬렉션 조회가 완료되었습니다"),

	/* 204 NO CONTENT */
	SUCCESS_LOGOUT(HttpStatus.NO_CONTENT, "로그아웃이 완료되었습니다");

	private final HttpStatus httpStatus;
	private final String message;
}
