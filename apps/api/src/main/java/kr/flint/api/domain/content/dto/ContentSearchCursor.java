package kr.flint.api.domain.content.dto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.util.StringUtils;

import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;

public record ContentSearchCursor(
    int version,
    SortMode sortMode,
    Integer bookmarkCount,
    Integer exactMatchRank,
    Double relevanceScore,
    Long contentId
) {
    private static final int CURRENT_VERSION = 1;
    private static final String DELIMITER = ":";

    public enum SortMode {
        POPULAR,
        KEYWORD
    }

    public ContentSearchCursor {
        if (version != CURRENT_VERSION || sortMode == null || contentId == null || contentId <= 0) {
            throw invalidCursor();
        }
        if (sortMode == SortMode.POPULAR && (bookmarkCount == null || bookmarkCount < 0)) {
            throw invalidCursor();
        }
        if (sortMode == SortMode.KEYWORD
            && (exactMatchRank == null || exactMatchRank < 0 || relevanceScore == null || relevanceScore < 0)) {
            throw invalidCursor();
        }
    }

    public static ContentSearchCursor popular(int bookmarkCount, Long contentId) {
        return new ContentSearchCursor(CURRENT_VERSION, SortMode.POPULAR, bookmarkCount, null, null, contentId);
    }

    public static ContentSearchCursor keyword(int exactMatchRank, double relevanceScore, Long contentId) {
        return new ContentSearchCursor(CURRENT_VERSION, SortMode.KEYWORD, null, exactMatchRank, relevanceScore, contentId);
    }

    public static ContentSearchCursor of(int bookmarkCount, Long contentId) {
        return popular(bookmarkCount, contentId);
    }

    public static ContentSearchCursor decodeNullable(String cursor) {
        return StringUtils.hasText(cursor) ? decode(cursor) : null;
    }

    public static ContentSearchCursor decode(String cursor) {
        try {
            if (!StringUtils.hasText(cursor)) {
                throw invalidCursor();
            }
            String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = payload.split(DELIMITER, -1);
            if (parts.length < 4) {
                throw invalidCursor();
            }

            int version = Integer.parseInt(parts[0]);
            if (version != CURRENT_VERSION) {
                throw invalidCursor();
            }
            SortMode mode = SortMode.valueOf(parts[1]);
            return switch (mode) {
                case POPULAR -> {
                    if (parts.length != 4) throw invalidCursor();
                    yield popular(Integer.parseInt(parts[2]), Long.parseLong(parts[3]));
                }
                case KEYWORD -> {
                    if (parts.length != 5) throw invalidCursor();
                    yield keyword(Integer.parseInt(parts[2]), Double.parseDouble(parts[3]), Long.parseLong(parts[4]));
                }
            };
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    public void validateSortMode(boolean keywordSearch) {
        SortMode expected = keywordSearch ? SortMode.KEYWORD : SortMode.POPULAR;
        if (sortMode != expected) {
            throw invalidCursor();
        }
    }

    public String encode() {
        String payload = sortMode == SortMode.POPULAR
            ? String.join(DELIMITER, String.valueOf(version), sortMode.name(), String.valueOf(bookmarkCount), String.valueOf(contentId))
            : String.join(DELIMITER, String.valueOf(version), sortMode.name(), String.valueOf(exactMatchRank), String.valueOf(relevanceScore), String.valueOf(contentId));
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static GeneralException invalidCursor() {
        return new GeneralException(ErrorCode.INVALID_INPUT, "cursor 형식이 올바르지 않습니다.");
    }
}
