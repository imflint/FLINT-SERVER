package kr.flint.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인별 오류 코드를 추상화하는 공통 인터페이스.
 * 각 도메인 모듈에서 이 인터페이스를 구현한 enum을 정의하여 사용.
 */
public interface AppError {

    HttpStatus getHttpStatus();

    String getTitle();

    String getCode();

    String getDetail();

    /**
     * 상세 메시지에 포맷 인자를 적용하여 반환.
     *
     * @param args 포맷 인자
     * @return 포맷된 상세 메시지
     */
    default String format(Object... args) {
        try {
            return String.format(getDetail(), args);
        } catch (Exception e) {
            return getDetail();
        }
    }
}
