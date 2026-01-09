package kr.flint.api.storage.exception;

import kr.flint.shared.exception.GeneralException;

public class StorageException extends GeneralException {

    public StorageException(StorageErrorCode errorCode) {
        super(errorCode);
    }

    public StorageException(StorageErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public StorageException(StorageErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
