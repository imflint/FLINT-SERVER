package kr.flint.api.domain.user.dto.response;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.taste.dto.response.UserKeywordProjection;

@Schema(description = "사용자 취향 키워드 응답")
public record UserKeywordsRes(
    @Schema(description = "키워드 목록")
    List<KeywordItem> keywords
) {
    public static UserKeywordsRes from(List<UserKeywordProjection> projections, Function<String, String> imageUrlResolver) {
        List<KeywordItem> items = projections.stream()
            .map(p -> KeywordItem.from(p, imageUrlResolver))
            .toList();
        return new UserKeywordsRes(items);
    }

    @Schema(description = "키워드 항목")
    public record KeywordItem(
		@Schema(description = "Color", example = "ORANGE")
		String color,
		@Schema(description = "순위", example = "1")
		int rank,
        @Schema(description = "키워드 이름", example = "힐링")
        String name,
        @Schema(description = "비율 (%)", example = "85")
        Integer percentage,
		@Schema(description = "이미지 url", example = "https.xxx.example.jpg")
		String imageUrl
    ) {
        public static KeywordItem from(UserKeywordProjection projection, Function<String, String> imageUrlResolver) {
            return new KeywordItem(
				projection.getLevel().getColor().toString(),
				projection.getRanking(),
                projection.getName(),
                projection.getPercentage(),
				imageUrlResolver.apply(projection.getImageUrl())
            );
        }
    }
}
