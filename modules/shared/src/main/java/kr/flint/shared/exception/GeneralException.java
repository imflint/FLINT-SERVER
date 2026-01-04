package kr.flint.shared.exception;

import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final AppError errorCode;

    public GeneralException(AppError errorCode) {
        super(errorCode.getDetail());
        this.errorCode = errorCode;
    }

    public GeneralException(AppError errorCode, Object... args) {
        super(errorCode.format(args));
        this.errorCode = errorCode;
    }

    public GeneralException(AppError errorCode, Throwable cause) {
        super(errorCode.getDetail(), cause);
        this.errorCode = errorCode;
    }

    public GeneralException(AppError errorCode, Throwable cause, Object... args) {
        super(errorCode.format(args), cause);
        this.errorCode = errorCode;
    }
}
