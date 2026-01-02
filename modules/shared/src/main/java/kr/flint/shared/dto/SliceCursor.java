package kr.flint.shared.dto;

import java.util.List;

public record SliceCursor<T>(
        List<T> items,
        String currentCursor,
        String nextCursor
) {

    public static <T> SliceCursor<T> of(List<T> items, String currentCursor, String nextCursor) {
        return new SliceCursor<>(items, currentCursor, nextCursor);
    }

    public boolean hasNext() {
        return nextCursor != null && !nextCursor.isEmpty();
    }
}
