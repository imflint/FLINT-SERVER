package kr.flint.api.domain.content.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.ott.dto.GetOttResponse;

@Schema(description = "OTT리스트 조회")
public record GetOttListRes(
	@ArraySchema(schema = @Schema(implementation = GetOttResponse.class))
	List<GetOttResponse> otts
) {
}
