package kr.flint.api.domain.user.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자가 생성한 컬렉션 목록 응답")
public record UserCollectionsResponse(
    @Schema(description = "컬렉션 목록")
    List<CollectionItem> collections
) {
    public static UserCollectionsResponse from(List<CollectionWithUserProjection> projections) {
        List<CollectionItem> items = projections.stream()
            .map(CollectionItem::from)
            .toList();
        return new UserCollectionsResponse(items);
    }

    @Schema(description = "컬렉션 항목")
    public record CollectionItem(
        @Schema(description = "컬렉션 ID", example = "123456789")
        String id,
        @Schema(description = "컬렉션 제목", example = "내가 좋아하는 영화")
        String title,
        @Schema(description = "컬렉션 이미지 URL")
        String image,
        @Schema(description = "작성자 프로필 이미지 URL")
        String profileImage,
        @Schema(description = "작성자 이름", example = "홍길동")
        String userName
    ) {
        public static CollectionItem from(CollectionWithUserProjection projection) {
            return new CollectionItem(
                String.valueOf(projection.getId()),
                projection.getTitle(),
                projection.getImage(),
                projection.getProfileImage(),
                projection.getUserName()
            );
        }
    }
}
