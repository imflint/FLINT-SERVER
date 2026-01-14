package kr.flint.api.domain.content.controller.spec;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.ott.dto.GetOttResponse;

@Schema(description = "콘텐츠별 OTT 목록 조회 성공 응답")
public record GetOttListSuccessResponse(

	@Schema(description = "성공 코드", example = "SUCCESS_FETCH")
	String code,

	@Schema(description = "성공 메시지", example = "조회 성공")
	String message,

	@Schema(description = "데이터")
	List<GetOttResponse> data

) {}
