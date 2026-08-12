package kr.flint.api.domain.exploration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탐색 작품")
public record ExploreContentRes(
	@Schema(description = "작품 ID", example = "801473411402740986", type = "string")
	Long contentId,
	@Schema(description = "제목", example = "인터스텔라")
	String title,
	@Schema(description = "포스터 이미지 URL")
	String imageUrl,
	@Schema(description = "제작 연도", example = "2014")
	int year
) {}
