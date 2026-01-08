package kr.flint.shared.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"status", "message", "data"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuccessResponse<T>(
	int status,
	String message,
	T data
) {
	//성공응답(데이터 없음)
	public static <T> SuccessResponse<T> of(SuccessCode successCode) {
		return new SuccessResponse<>(successCode.getHttpStatus().value(), successCode.getMessage(), null);
	}

	//성공응답(데이터 있음)
	public static <T> SuccessResponse<T> of(SuccessCode successCode, T data) {
		return new SuccessResponse<>(successCode.getHttpStatus().value(), successCode.getMessage(), data);
	}
}
