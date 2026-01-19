package kr.flint.api.domain.user.dto.response;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.api.domain.collection.util.CollectionImageProcessor;

@Schema(description = "컬렉션 목록 응답")
public record UserCollectionsRes(
    @ArraySchema(schema = @Schema(implementation = CollectionItem.class))
    List<CollectionItem> collections
) {
    public static UserCollectionsRes from(
        List<CollectionWithUserDto> projections,
        Map<Long, List<String>> contentImagesMap,
        Set<Long> bookmarkedCollectionIds,
        Function<String, String> imageUrlResolver
    ) {
        List<CollectionItem> items = projections.stream()
            .map(p -> CollectionItem.from(
                p,
                contentImagesMap.getOrDefault(p.id(), List.of()),
                bookmarkedCollectionIds.contains(p.id()),
                imageUrlResolver
            ))
            .toList();
        return new UserCollectionsRes(items);
    }

    @Schema(description = "컬렉션 항목")
    public record CollectionItem(
        @Schema(description = "컬렉션 ID", example = "1")
        Long id,
        @Schema(description = "컬렉션 썸네일 (첫 번째 작품 포스터)", example = "https://example.com/thumbnail.jpg")
        String thumbnailUrl,
        @Schema(description = "컬렉션 제목", example = "주말에 보기 좋은 영화")
        String title,
        @Schema(description = "컬렉션 설명", example = "편하게 볼 수 있는 영화들")
        String description,
        @ArraySchema(schema = @Schema(implementation = String.class, example = "https://example.com/image.jpg"))
        List<String> imageList,
        @Schema(description = "북마크 수", example = "15")
        Integer bookmarkCount,
        @Schema(description = "북마크 여부", example = "true")
        Boolean isBookmarked,
        @Schema(description = "작성자 ID", example = "123")
        Long userId,
        @Schema(description = "작성자 닉네임", example = "플린트")
        String nickname,
        @Schema(description = "작성자 프로필 URL", example = "https://example.com/profile.jpg")
        String profileUrl
    ) {
        public static CollectionItem from(
            CollectionWithUserDto dto,
            List<String> contentPosters,
            boolean isBookmarked,
            Function<String, String> imageUrlResolver
        ) {
            List<String> resolvedImages = CollectionImageProcessor.limitAndResolveImages(contentPosters, imageUrlResolver);
            String thumbnail = CollectionImageProcessor.selectThumbnail(resolvedImages);

            return new CollectionItem(
                dto.id(),
                thumbnail,
                dto.title(),
                dto.description(),
                resolvedImages,
                dto.bookmarkCount(),
                isBookmarked,
                dto.userId(),
                dto.nickname(),
                imageUrlResolver.apply(dto.profileImage())
            );
        }
    }
}
