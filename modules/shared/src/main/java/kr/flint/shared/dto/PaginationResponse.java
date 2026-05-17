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

    public static <T> PaginationResponse<T> ofOffset(List<T> data, int page, int size, long totalElements) {
        return new PaginationResponse<>(
                data,
                PaginationMeta.ofOffset(data != null ? data.size() : 0, page, size, totalElements)
        );
    }
}
