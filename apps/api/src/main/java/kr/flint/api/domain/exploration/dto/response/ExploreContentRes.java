package kr.flint.api.domain.exploration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탐색 작품")
public record ExploreContentRes(
	@Schema(description = "작품 ID", example = "801473411402740986", type = "string")
	Long contentId,
	@Schema(description = "제목", example = "인터스텔라")
	String title,
	@Schema(description = "대표 컬렉션 작성자가 작성한 작품 선정 이유", example = "우주 저편의 감정을 섬세하게 풀어낸 작품이에요.")
	String description,
	@Schema(description = "포스터 이미지 URL")
	String imageUrl,
	@Schema(description = "제작 연도", example = "2014")
	int year,
	@Schema(description = "자세히 보기 이동용 대표 컬렉션 ID (여러 공개 컬렉션에 속하면 최신 1개)", example = "801473411402741000", type = "string")
	Long collectionId
) {}
