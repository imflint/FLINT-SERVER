package kr.flint.content.exception;

import kr.flint.shared.exception.GeneralException;

public class ContentException extends GeneralException {

	public ContentException(ContentErrorCode errorCode) {
		super(errorCode);
	}

	public ContentException(ContentErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}

	public ContentException(ContentErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
