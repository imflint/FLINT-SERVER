package kr.flint.admin.domain.collection.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.flint.collection.dto.CollectionCreateCommand.ContentInput;

@Schema(description = "관리자 컬렉션 포함 콘텐츠 수정 요청")
public record AdminCollectionContentUpdateReq(
    @Schema(description = "콘텐츠 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "콘텐츠는 필수입니다.")
    Long contentId,

    @Schema(description = "스포일러 포함 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "스포일러 여부는 필수입니다.")
    Boolean isSpoiler,

    @Schema(description = "콘텐츠 선정 이유", example = "대표 장면이 좋아요", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "콘텐츠 메모는 필수입니다.")
    String reason,

    @Schema(description = "콘텐츠별 커스텀 이미지 key 또는 URL 목록")
    @JsonAlias("customImageUrls")
    @Nullable
    List<String> customImages
) {
    public ContentInput toInput() {
        return ContentInput.of(contentId, isSpoiler, reason, customImages);
    }
}
