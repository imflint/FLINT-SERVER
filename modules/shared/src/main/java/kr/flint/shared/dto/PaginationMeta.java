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

        // Offset 전용
        @Schema(description = "현재 페이지 번호 (1부터 시작)", example = "1")
        Integer currentPage,

        @Schema(description = "전체 페이지 수", example = "10")
        Integer totalPages,

        @Schema(description = "전체 아이템 수", example = "100")
        Long totalElements,

        // Cursor 전용
        @Schema(description = "다음 페이지 커서")
        String nextCursor
) {

    public static PaginationMeta ofOffset(int returned, int currentPage, int totalPages, long totalElements) {
        return new PaginationMeta(
                PageType.OFFSET,
                returned,
                currentPage,
                totalPages,
                totalElements,
                null
        );
    }

    public static PaginationMeta ofCursor(int returned, String nextCursor) {
        return new PaginationMeta(
                PageType.CURSOR,
                returned,
                null,
                null,
                null,
                nextCursor
        );
    }
}
