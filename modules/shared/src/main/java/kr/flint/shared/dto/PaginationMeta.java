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

        // cursor
        @Schema(description = "다음 페이지 커서")
        String nextCursor,

        // offset
        @Schema(description = "현재 페이지 번호", example = "1")
        Integer page,

        @Schema(description = "페이지 크기", example = "20")
        Integer size,

        @Schema(description = "전체 아이템 수", example = "123")
        Long totalElements,

        @Schema(description = "전체 페이지 수", example = "7")
        Integer totalPages
) {

    public static PaginationMeta ofCursor(int returned, String nextCursor) {
        return new PaginationMeta(
                PageType.CURSOR,
                returned,
                nextCursor,
                null,
                null,
                null,
                null
        );
    }

    public static PaginationMeta ofOffset(int returned, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PaginationMeta(
                PageType.OFFSET,
                returned,
                null,
                page,
                size,
                totalElements,
                totalPages
        );
    }
}
