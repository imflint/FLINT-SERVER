package kr.flint.api.admin.domain.content.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentCreateCommand;

@Schema(description = "관리자 수동 콘텐츠 등록 요청")
public record AdminContentCreateReq(
    @Schema(description = "콘텐츠 종류", requiredMode = Schema.RequiredMode.REQUIRED, example = "MOVIE")
    @NotNull(message = "콘텐츠 종류는 필수입니다.")
    MediaType mediaType,

    @Schema(description = "사용자에게 표시되는 제목", requiredMode = Schema.RequiredMode.REQUIRED, example = "수동 등록 작품")
    @NotBlank(message = "콘텐츠 제목은 필수입니다.")
    @Size(max = 255, message = "콘텐츠 제목은 255자 이하여야 합니다.")
    String title,

    @Schema(description = "개봉/방영 연도. 미입력 시 미상(0)", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "2026")
    @Min(value = 0, message = "연도는 0 이상이어야 합니다.")
    Integer year,

    @Schema(description = "감독/작가", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "감독/작가는 255자 이하여야 합니다.")
    String author,

    @Schema(description = "콘텐츠 설명", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String description,

    @Schema(description = "포스터 이미지 키 또는 URL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "포스터는 255자 이하여야 합니다.")
    String poster,

    @Schema(description = "표준 장르명 목록. 미입력 시 장르 없음", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "[\"드라마\", \"SF\"]")
    List<@NotBlank(message = "장르명은 비어 있을 수 없습니다.") @Size(max = 50, message = "장르명은 50자 이하여야 합니다.") String> genreNames
) {
    public ContentCreateCommand toCommand() {
        return ContentCreateCommand.of(mediaType, title.trim(), year == null ? 0 : year,
            normalizeOptionalText(author), normalizeOptionalText(description), normalizeOptionalText(poster), genreNames);
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
