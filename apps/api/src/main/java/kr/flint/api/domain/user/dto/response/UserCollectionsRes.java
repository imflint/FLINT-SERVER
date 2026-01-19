package kr.flint.api.domain.user.dto.response;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자가 생성한 컬렉션 목록 응답")
public record UserCollectionsRes(
    @ArraySchema(schema = @Schema(implementation = CollectionDetailItem.class))
    List<CollectionDetailItem> collections
) {
    private static final int MAX_IMAGE_COUNT = 2;

    public static UserCollectionsRes from(
        List<CollectionWithUserProjection> projections,
        Map<Long, List<String>> contentImagesMap,
        Set<Long> bookmarkedCollectionIds,
        Function<String, String> imageUrlResolver
    ) {
        List<CollectionDetailItem> items = projections.stream()
            .map(p -> CollectionDetailItem.from(
                p,
                contentImagesMap.getOrDefault(p.getId(), List.of()),
                bookmarkedCollectionIds.contains(p.getId()),
                imageUrlResolver
            ))
            .toList();
        return new UserCollectionsRes(items);
    }

    @Schema(description = "컬렉션 상세 항목")
    public record CollectionDetailItem(
        @Schema(description = "컬렉션 ID", example = "1")
        @JsonSerialize(using = ToStringSerializer.class)
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
        @JsonSerialize(using = ToStringSerializer.class)
        Long userId,
        @Schema(description = "작성자 닉네임", example = "플린트")
        String nickname,
        @Schema(description = "작성자 프로필 URL", example = "https://example.com/profile.jpg")
        String profileUrl
    ) {
        public static CollectionDetailItem from(
            CollectionWithUserProjection projection,
            List<String> contentPosters,
            boolean isBookmarked,
            Function<String, String> imageUrlResolver
        ) {
            // 최대 2개의 이미지만 사용
            List<String> limitedPosters = contentPosters.stream()
                .limit(MAX_IMAGE_COUNT)
                .map(imageUrlResolver)
                .toList();

            // 첫 번째 이미지를 썸네일로 사용
            String thumbnail = limitedPosters.isEmpty() ? null : limitedPosters.get(0);

            return new CollectionDetailItem(
                projection.getId(),
                thumbnail,
                projection.getTitle(),
                projection.getDescription(),
                limitedPosters,
                projection.getBookmarkCount(),
                isBookmarked,
                projection.getUserId(),
                projection.getNickname(),
                imageUrlResolver.apply(projection.getProfileImage())
            );
        }
    }
}
