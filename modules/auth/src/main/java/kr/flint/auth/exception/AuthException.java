package kr.flint.auth.exception;

import kr.flint.shared.exception.GeneralException;

public class AuthException extends GeneralException {

    /**
     * Creates an AuthException for the given authentication error code.
     *
     * @param errorCode the AuthErrorCode that identifies the specific authentication error
     */
    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * Constructs an AuthException with the specified authentication error code and
     * optional message arguments.
     *
     * @param errorCode the specific authentication error code
     * @param args      optional arguments to format the error message associated with the code
     */
    public AuthException(AuthErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    /**
     * Creates an AuthException with the specified authentication error code and underlying cause.
     *
     * @param errorCode the AuthErrorCode representing the specific authentication error
     * @param cause the underlying cause of this exception
     */
    public AuthException(AuthErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}