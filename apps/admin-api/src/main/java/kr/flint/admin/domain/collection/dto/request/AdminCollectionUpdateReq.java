package kr.flint.admin.domain.collection.dto.request;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.flint.collection.dto.CollectionUpdateCommand;

@Schema(description = "관리자 컬렉션 수정 요청")
public record AdminCollectionUpdateReq(
    @Schema(description = "컬렉션 대표 이미지 key 또는 URL")
    @Nullable
    String imageUrl,

    @Schema(description = "컬렉션 제목", example = "주말에 보기 좋은 영화", maxLength = 20, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "컬렉션 제목은 필수입니다.")
    @Size(max = 20, message = "컬렉션 제목은 20자 이하여야 합니다.")
    String title,

    @Schema(description = "컬렉션 설명", maxLength = 200)
    @Size(max = 200, message = "컬렉션 설명은 200자 이하여야 합니다.")
    String description,

    @Schema(description = "공개 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "공개 여부는 필수입니다.")
    Boolean isPublic,

    @Schema(description = "수정 후 컬렉션에 포함될 콘텐츠 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "콘텐츠 목록은 필수입니다.")
    @Size(min = 1, message = "콘텐츠는 1개 이상 입력해야 합니다.")
    List<@NotNull(message = "콘텐츠 정보는 필수입니다.") @Valid AdminCollectionContentUpdateReq> contentList
) {
    @AssertTrue(message = "콘텐츠는 중복 없이 입력해야 합니다.")
    public boolean isUniqueContentIds() {
        if (contentList == null) {
            return true;
        }

        Set<Long> contentIds = new HashSet<>();
        for (AdminCollectionContentUpdateReq content : contentList) {
            if (content == null || content.contentId() == null) {
                continue;
            }
            if (!contentIds.add(content.contentId())) {
                return false;
            }
        }
        return true;
    }

    public List<Long> contentIds() {
        if (contentList == null) {
            return List.of();
        }
        return contentList.stream()
            .map(AdminCollectionContentUpdateReq::contentId)
            .toList();
    }

    public CollectionUpdateCommand toCommand() {
        return CollectionUpdateCommand.of(
            title,
            description,
            imageUrl,
            isPublic,
            contentList.stream()
                .map(AdminCollectionContentUpdateReq::toInput)
                .toList()
        );
    }
}
