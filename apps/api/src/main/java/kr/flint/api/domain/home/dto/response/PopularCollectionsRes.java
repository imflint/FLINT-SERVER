package kr.flint.api.domain.home.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인기 컬렉션 목록 응답 (좌우 스크롤용, 최대 10개)")
public record PopularCollectionsRes(
    @ArraySchema(schema = @Schema(implementation = PopularCollectionCardRes.class))
    List<PopularCollectionCardRes> collections
) {
    public static PopularCollectionsRes from(List<PopularCollectionCardRes> collections) {
        return new PopularCollectionsRes(collections);
    }
}
