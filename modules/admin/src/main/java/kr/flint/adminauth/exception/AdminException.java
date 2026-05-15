package kr.flint.adminauth.exception;

import kr.flint.shared.exception.GeneralException;

public class AdminException extends GeneralException {

    public AdminException(AdminErrorCode errorCode) {
        super(errorCode);
    }
}
