package kr.flint.api.domain.home.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 컬렉션 목록 응답")
public record RecommendedCollectionsRes(
    @ArraySchema(schema = @Schema(implementation = CollectionCardRes.class))
    List<CollectionCardRes> collections
) {
    public static RecommendedCollectionsRes from(List<CollectionCardRes> collections) {
        return new RecommendedCollectionsRes(collections);
    }
}
