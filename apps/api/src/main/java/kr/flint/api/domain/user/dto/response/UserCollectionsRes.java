package kr.flint.api.domain.user.dto.response;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자가 생성한 컬렉션 목록 응답")
public record UserCollectionsRes(
    @Schema(description = "컬렉션 목록")
    List<CollectionItem> collections
) {
    public static UserCollectionsRes from(List<CollectionWithUserProjection> projections, Function<String, String> imageUrlResolver) {
        List<CollectionItem> items = projections.stream()
            .map(p -> CollectionItem.from(p, imageUrlResolver))
            .toList();
        return new UserCollectionsRes(items);
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
        public static CollectionItem from(CollectionWithUserProjection projection, Function<String, String> imageUrlResolver) {
            return new CollectionItem(
                String.valueOf(projection.getId()),
                projection.getTitle(),
                imageUrlResolver.apply(projection.getImage()),
                projection.getProfileImage(),
                projection.getUserName()
            );
        }
    }
}
