package kr.flint.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "RFC 9457 오류 응답")
@JsonPropertyOrder({"title", "status", "detail", "instance", "errorCode", "additionalInfo"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        @Schema(description = "오류 제목", example = "Bad Request")
        String title,

        @Schema(description = "HTTP 상태 코드", example = "400")
        Integer status,

        @Schema(description = "오류 상세 설명", example = "입력값이 올바르지 않습니다.")
        String detail,

        @Schema(description = "오류가 발생한 URI", example = "/v1/users/123")
        String instance,

        @Schema(description = "오류 코드 (클라이언트 분기용)", example = "COMMON.BAD_REQUEST")
        String errorCode,

        @Schema(description = "추가 정보 (검증 오류 필드 등)")
        Map<String, String> additionalInfo
) {

    public ProblemDetail {
        additionalInfo = (additionalInfo == null) ? null : Map.copyOf(additionalInfo);
    }

    public static ProblemDetail of(AppError error, String instance) {
        return of(error, error.getDetail(), instance, null);
    }

    /**
     * 커스텀 메시지
     */
    public static ProblemDetail of(AppError error, String customDetail, String instance) {
        return of(error, customDetail, instance, null);
    }

    /**
     * 추가 정보를 포함
     */
    public static ProblemDetail of(AppError error, String customDetail, String instance, Map<String, String> additionalInfo) {
        return new ProblemDetail(
                error.getTitle(),
                error.getHttpStatus().value(),
                customDetail,
                instance,
                error.getCode(),
                additionalInfo
        );
    }

    /**
     * GeneralException으로부터 오류 응답 생성.
     */
    public static ProblemDetail from(GeneralException ex, String instance) {
        return of(ex.getErrorCode(), ex.getMessage(), instance);
    }
}
