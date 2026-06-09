package kr.flint.api.domain.content.dto;

import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크한 작품 조회")
public record GetContentListRes(
	@Schema(description = "북마크한 작품 개수", example = "12")
	int totalCount,
	@ArraySchema(schema = @Schema(implementation = Content.class))
	List<Content> contents
) {
	public static GetContentListRes from(
		List<GetContentDetailRes> contents,
		Set<Long> bookmarkedContentIds
	) {
		List<Content> items = contents.stream()
			.map(content -> Content.from(content, bookmarkedContentIds.contains(content.id())))
			.toList();
		return new GetContentListRes(items.size(), items);
	}

	@Schema(description = "북마크 작품 항목")
	public record Content(
		@Schema(description = "콘텐츠 ID", example = "1")
		Long id,
		@Schema(description = "콘텐츠 제목", example = "인셉션")
		String title,
		@Schema(description = "콘텐츠 이미지 URL", example = "https://example.com/poster.jpg")
		String imageUrl,
		@Schema(description = "개봉/방영 연도", example = "2010")
		int year,
		@Schema(description = "북마크 수", example = "15")
		int bookmarkCount,
		@Schema(description = "로그인한 사용자의 북마크 여부", example = "true")
		Boolean isBookmarked,
		@ArraySchema(schema = @Schema(implementation = GetContentDetailRes.GetOttSimpleRes.class))
		List<GetContentDetailRes.GetOttSimpleRes> getOttSimpleList
	) {
		public static Content from(GetContentDetailRes content, boolean isBookmarked) {
			return new Content(
				content.id(),
				content.title(),
				content.imageUrl(),
				content.year(),
				content.bookmarkCount(),
				isBookmarked,
				content.getOttSimpleList()
			);
		}
	}
}
