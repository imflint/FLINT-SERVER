package kr.flint.api.domain.user.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.taste.dto.response.UserKeywordProjection;

@Schema(description = "사용자 취향 키워드 응답")
public record UserKeywordsResponse(
    @Schema(description = "키워드 목록")
    List<KeywordItem> keywords
) {
    public static UserKeywordsResponse from(List<UserKeywordProjection> projections) {
        List<KeywordItem> items = projections.stream()
            .map(KeywordItem::from)
            .toList();
        return new UserKeywordsResponse(items);
    }

    @Schema(description = "키워드 항목")
    public record KeywordItem(
        @Schema(description = "키워드 이름", example = "힐링")
        String name,
        @Schema(description = "비율 (%)", example = "85")
        Integer percentage
    ) {
        public static KeywordItem from(UserKeywordProjection projection) {
            return new KeywordItem(
                projection.getName(),
                projection.getPercentage()
            );
        }
    }
}
