package kr.flint.system.exception;

import kr.flint.shared.exception.GeneralException;

public class SystemException extends GeneralException {

	public SystemException(SystemErrorCode errorCode) {
		super(errorCode);
	}

	public SystemException(SystemErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}
}
