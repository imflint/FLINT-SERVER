package kr.flint.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import kr.flint.shared.exception.AppError;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import kr.flint.shared.exception.ProblemDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(GeneralException e, HttpServletRequest request) {
        AppError errorCode = e.getErrorCode();
        log.warn("GeneralException 발생: {} - {}", errorCode.getCode(), e.getMessage());

        ProblemDetail problemDetail = ProblemDetail.from(e, request.getRequestURI());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("입력값 검증 오류: {}", e.getMessage());
        AppError errorCode = ErrorCode.INVALID_INPUT;

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult()
                .getAllErrors()
                .forEach(error -> {
                    if (error instanceof FieldError fieldError) {
                        errors.put(fieldError.getField(), error.getDefaultMessage());
                    } else {
                        errors.put(error.getObjectName(), error.getDefaultMessage());
                    }
                });

        ProblemDetail problemDetail = ProblemDetail.of(
                errorCode,
                "입력값이 올바르지 않습니다.",
                request.getRequestURI(),
                errors
        );

        return ResponseEntity
                .badRequest()
                .body(problemDetail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("요청 파라미터 누락: {}", e.getParameterName());
        AppError errorCode = ErrorCode.MISSING_PARAMETER;

        String message = String.format("필수 요청 파라미터 [%s] 누락", e.getParameterName());

        ProblemDetail problemDetail = ProblemDetail.of(
                errorCode,
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(problemDetail);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.debug("리소스 없음: {}", request.getRequestURI());
        AppError errorCode = ErrorCode.NOT_FOUND;

        ProblemDetail problemDetail = ProblemDetail.of(
                errorCode,
                "요청한 리소스를 찾을 수 없습니다.",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception e, HttpServletRequest request) {
        log.error("예상치 못한 오류 발생: {}", e.getMessage(), e);
        AppError errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        ProblemDetail problemDetail = ProblemDetail.of(
                errorCode,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(problemDetail);
    }
}
