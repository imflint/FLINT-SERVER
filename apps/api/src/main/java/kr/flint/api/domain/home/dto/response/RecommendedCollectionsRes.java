package kr.flint.api.domain.home.dto.response;

import java.util.List;

public record RecommendedCollectionsRes(
    List<CollectionCardRes> collections
) {
    public static RecommendedCollectionsRes from(List<CollectionCardRes> collections) {
        return new RecommendedCollectionsRes(collections);
    }
}
