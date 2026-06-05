package kr.flint.bookmark.exception;

import kr.flint.shared.exception.GeneralException;

public class BookmarkException extends GeneralException {

	public BookmarkException(BookmarkErrorCode errorCode) {
		super(errorCode);
	}

	public BookmarkException(BookmarkErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}

	public BookmarkException(BookmarkErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
