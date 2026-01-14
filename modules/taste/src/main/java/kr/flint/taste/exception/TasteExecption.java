package kr.flint.taste.exception;

import kr.flint.shared.exception.GeneralException;

public class TasteExecption extends GeneralException {

	public TasteExecption(TasteErrorCode errorCode) {
		super(errorCode);
	}

	public TasteExecption(TasteErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}

	public TasteExecption(TasteErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
