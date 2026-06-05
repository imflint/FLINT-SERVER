package kr.flint.bookmark.exception;

import kr.flint.shared.exception.AppError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BookmarkErrorCode implements AppError {

	CONTENT_BOOKMARK_MIN_LIMIT(
		HttpStatus.BAD_REQUEST,
		"BOOKMARK.CONTENT_MIN_LIMIT",
		"Content Bookmark Minimum Limit",
		"북마크한 작품은 최소 5개 이상 유지해야 하므로 취소할 수 없습니다."
	);

	private final HttpStatus httpStatus;
	private final String code;
	private final String title;
	private final String detail;
}
