package kr.flint.ott.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GetOttResponse(
	@Schema(type = "string")
	Long ottId,
	String name,
	String logoUrl,
	String contentUrl
) {
}
