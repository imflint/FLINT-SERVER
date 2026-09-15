package kr.flint.exploration.exception;

import kr.flint.shared.exception.GeneralException;

public class ExplorationException extends GeneralException {

	public ExplorationException(ExplorationErrorCode errorCode) {
		super(errorCode);
	}
}
