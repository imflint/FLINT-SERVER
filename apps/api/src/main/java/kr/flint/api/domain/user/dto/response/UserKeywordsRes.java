package kr.flint.api.domain.user.dto.response;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.taste.dto.response.UserKeywordProjection;

@Schema(description = "사용자 취향 키워드 응답")
public record UserKeywordsRes(
    @ArraySchema(schema = @Schema(implementation = KeywordItem.class))
    List<KeywordItem> keywords
) {
    public static UserKeywordsRes from(List<UserKeywordProjection> projections, Function<String, String> imageUrlResolver) {
        List<KeywordItem> items = IntStream.range(0, Math.min(projections.size(), 6))
            .mapToObj(index -> KeywordItem.from(projections.get(index), index + 1, imageUrlResolver))
            .toList();
        return new UserKeywordsRes(items);
    }

    @Schema(description = "키워드 항목")
    public record KeywordItem(
		@Schema(description = "키워드 색상 (PINK, GREEN, ORANGE, YELLOW, BLUE)", example = "ORANGE")
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
        public static KeywordItem from(
			UserKeywordProjection projection,
			int normalizedRank,
			Function<String, String> imageUrlResolver
		) {
            return new KeywordItem(
				projection.getLevel().getColor().toString(),
				normalizedRank,
                projection.getName(),
                projection.getPercentage(),
				imageUrlResolver.apply(projection.getImageUrl())
            );
        }
    }
}
