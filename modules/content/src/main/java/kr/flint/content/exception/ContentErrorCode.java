package kr.flint.content.exception;

import org.springframework.http.HttpStatus;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContentErrorCode implements AppError {
	CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT.NOT_FOUND", "Content Not Found", "작품을 찾을 수 없습니다."),
	TMDB_OTT_NOT_FOUND(HttpStatus.NOT_FOUND, "TMDB.NOT_FOUND", "TMDB Not Found", "TMDB에서 OTT정보를 찾을 수 없습니다."),
	TMDB_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "TMDB.NOT_FOUND", "TMDB Not Found", "TMDB에서 작품을 찾을 수 없습니다."),
	GENRE_NOT_FOUND(HttpStatus.NOT_FOUND, "GENRE.NOT_FOUND", "GENRE Not Found", "장르를 찾을 수 없습니다.");


	private final HttpStatus httpStatus;
	private final String code;
	private final String title;
	private final String detail;
}
