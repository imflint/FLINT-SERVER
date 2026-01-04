package kr.flint.shared.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "페이지네이션 응답")
@JsonPropertyOrder({"data", "meta"})
public record PaginationResponse<T>(
        @Schema(description = "데이터 목록")
        List<T> data,

        @Schema(description = "페이지 메타 정보")
        PaginationMeta meta
) {

    // =============== Offset 기반 ===============

    public static <T> PaginationResponse<T> ofOffset(Page<T> page) {
        int currentPage = page.getNumber() + 1; // 0-based to 1-based
        int returned = page.getNumberOfElements();
        int totalPages = page.getTotalPages();
        long totalElements = page.getTotalElements();

        return new PaginationResponse<>(
                page.getContent(),
                PaginationMeta.ofOffset(returned, currentPage, totalPages, totalElements)
        );
    }

    /**
     * 직접 생성
     */
    public static <T> PaginationResponse<T> ofOffset(List<T> data, int currentPage, int totalPages, long totalElements) {
        return new PaginationResponse<>(
                data,
                PaginationMeta.ofOffset(data.size(), currentPage, totalPages, totalElements)
        );
    }

    // ==================== Cursor 기반 ====================

    public static <T> PaginationResponse<T> ofCursor(SliceCursor<T> slice) {
        List<T> data = slice.items();
        int returned = data != null ? data.size() : 0;

        return new PaginationResponse<>(
                data,
                PaginationMeta.ofCursor(returned, slice.nextCursor())
        );
    }

    /**
     * 직접 생성
     */
    public static <T> PaginationResponse<T> ofCursor(List<T> data, String nextCursor) {
        return new PaginationResponse<>(
                data,
                PaginationMeta.ofCursor(data != null ? data.size() : 0, nextCursor)
        );
    }
}
