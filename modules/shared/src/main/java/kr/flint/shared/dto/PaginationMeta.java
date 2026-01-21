package kr.flint.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "페이지네이션 메타 정보")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginationMeta(
        // 공통
        @Schema(description = "페이지네이션 타입", example = "OFFSET")
        PageType type,

        @Schema(description = "현재 페이지 반환된 아이템 수", example = "10")
        Integer returned,

        @Schema(description = "다음 페이지 커서")
        String nextCursor
) {

    public static PaginationMeta ofCursor(int returned, String nextCursor) {
        return new PaginationMeta(
                PageType.CURSOR,
                returned,
                nextCursor
        );
    }
}
