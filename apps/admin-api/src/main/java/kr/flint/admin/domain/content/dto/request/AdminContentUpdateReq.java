package kr.flint.admin.domain.content.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.flint.content.dto.ContentUpdateCommand;

@Schema(description = "관리자 콘텐츠 수정 요청")
public record AdminContentUpdateReq(
    @Schema(description = "콘텐츠 제목", example = "인셉션")
    @Size(max = 255, message = "콘텐츠 제목은 255자 이하여야 합니다.")
    String title,

    @Schema(description = "개봉/방영 연도", example = "2010")
    @Min(value = 0, message = "연도는 0 이상이어야 합니다.")
    Integer year,

    @Schema(description = "감독/작가", example = "크리스토퍼 놀란")
    @Size(max = 255, message = "감독/작가는 255자 이하여야 합니다.")
    String author,

    @Schema(description = "콘텐츠 설명")
    String description,

    @Schema(description = "포스터 이미지 키 또는 URL", example = "/poster/inception.jpg")
    String poster,

    @Schema(description = "장르명 목록. null이면 장르를 유지하고, 빈 배열이면 장르를 모두 제거합니다.", example = "[\"액션\", \"SF\"]")
    List<@NotBlank(message = "장르명은 비어 있을 수 없습니다.") @Size(max = 50, message = "장르명은 50자 이하여야 합니다.") String> genreNames
) {
    public ContentUpdateCommand toCommand() {
        return ContentUpdateCommand.of(title, year, author, description, poster, genreNames);
    }
}
