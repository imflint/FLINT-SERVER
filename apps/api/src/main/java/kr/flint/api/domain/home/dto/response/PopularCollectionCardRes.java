package kr.flint.api.domain.home.dto.response;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.api.domain.collection.util.CollectionImageProcessor;
import kr.flint.api.domain.home.dto.projection.CollectionCardDto;

@Schema(description = "인기 컬렉션 카드 정보")
public record PopularCollectionCardRes(
    @Schema(description = "컬렉션 ID", example = "800388257884431200")
    Long id,
    @Schema(description = "컬렉션 카드 배경 사진", example = "https://cdn.flint.kr/collection/cover/800388.jpg")
    String thumbnailUrl,
    @Schema(description = "컬렉션 제목", example = "주말에 보기 좋은 한국 영화")
    String title,
    @Schema(description = "컬렉션 설명", example = "편하게 볼 수 있는 영화들")
    String description,
    @ArraySchema(schema = @Schema(implementation = String.class, example = "https://cdn.flint.kr/content/poster/123.jpg"))
    List<String> imageList,
    @Schema(description = "북마크 수", example = "15")
    Integer bookmarkCount,
    @Schema(description = "컬렉션 작성자 닉네임", example = "플린트")
    String nickname,
    @Schema(description = "컬렉션 작성자 프로필 사진", example = "https://cdn.flint.kr/user/profile/123.jpg")
    String profileImageUrl
) {
    public static PopularCollectionCardRes from(
        CollectionCardDto dto,
        List<String> contentPosters,
        Function<String, String> imageUrlResolver
    ) {
        List<String> resolvedImages = CollectionImageProcessor.limitAndResolveImages(contentPosters, imageUrlResolver);

        return new PopularCollectionCardRes(
            dto.id(),
            imageUrlResolver.apply(dto.image()),
            dto.title(),
            dto.description(),
            resolvedImages,
            dto.bookmarkCount(),
            dto.nickname(),
            imageUrlResolver.apply(dto.profileImage())
        );
    }
}
