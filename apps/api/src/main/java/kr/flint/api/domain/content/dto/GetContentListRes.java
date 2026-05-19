package kr.flint.api.domain.content.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크한 작품 조회")
public record GetContentListRes(
	@Schema(description = "북마크한 작품 개수", example = "12")
	int totalCount,
	@ArraySchema(schema = @Schema(implementation = GetContentDetailRes.class))
	List<GetContentDetailRes> contents
) {
	public static GetContentListRes from(List<GetContentDetailRes> contents) {
		return new GetContentListRes(contents.size(), contents);
	}
}
