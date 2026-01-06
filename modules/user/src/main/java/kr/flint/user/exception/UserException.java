package kr.flint.user.exception;

import kr.flint.shared.exception.GeneralException;

public class UserException extends GeneralException {

    /**
     * Creates a UserException with the specified user error code.
     *
     * @param errorCode the error code representing the user-related error
     */
    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * Create a UserException using the specified user error code and optional message arguments.
     *
     * The provided errorCode identifies the user-related error. The varargs `args` supply values used
     * to format the error message associated with that code.
     *
     * @param errorCode the UserErrorCode representing the specific user error
     * @param args optional arguments to format the error message for the given error code
     */
    public UserException(UserErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    /**
     * Creates a UserException initialized with the specified user error code and underlying cause.
     *
     * @param errorCode the UserErrorCode that categorizes this exception
     * @param cause the root cause of this exception (may be null)
     */
    public UserException(UserErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}