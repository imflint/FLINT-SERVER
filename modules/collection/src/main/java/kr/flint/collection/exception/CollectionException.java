package kr.flint.collection.exception;

import kr.flint.shared.exception.GeneralException;

public class CollectionException extends GeneralException {

	public CollectionException(CollectionErrorCode errorCode) {
		super(errorCode);
	}

	public CollectionException(CollectionErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}

	public CollectionException(CollectionErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
