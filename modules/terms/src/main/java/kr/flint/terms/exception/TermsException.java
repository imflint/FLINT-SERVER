package kr.flint.terms.exception;

import kr.flint.shared.exception.GeneralException;

public class TermsException extends GeneralException {

	public TermsException(TermsErrorCode errorCode) {
		super(errorCode);
	}

	public TermsException(TermsErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}
}
