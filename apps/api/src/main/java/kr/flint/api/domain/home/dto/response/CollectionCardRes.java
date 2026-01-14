package kr.flint.api.domain.home.dto.response;

import kr.flint.api.domain.home.dto.projection.CollectionCardProjection;

public record CollectionCardRes(
    String id,
    String title,
    String image,
    String profileImage,
    String userName
) {
    public static CollectionCardRes from(CollectionCardProjection projection) {
        return new CollectionCardRes(
            String.valueOf(projection.getId()),
            projection.getTitle(),
            projection.getImage(),
            projection.getProfileImage(),
            projection.getUserName()
        );
    }
}
