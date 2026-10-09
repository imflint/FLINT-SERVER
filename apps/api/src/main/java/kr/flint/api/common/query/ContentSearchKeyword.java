package kr.flint.api.common.query;

import java.util.Locale;

import kr.flint.content.domain.ContentTitleNormalizer;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;

public record ContentSearchKeyword(String raw, String normalized) {
    public static ContentSearchKeyword ofNullable(String keyword) {
        if (keyword == null || keyword.codePoints()
            .allMatch(value -> Character.isWhitespace(value) || Character.isSpaceChar(value))) {
            return null;
        }
        String raw = keyword.trim();
        String normalized = ContentTitleNormalizer.normalizeNullable(raw);
        if (normalized == null || normalized.codePointCount(0, normalized.length()) < 2) {
            throw new GeneralException(ErrorCode.INVALID_INPUT, "keyword는 2자 이상이어야 합니다.");
        }
        return new ContentSearchKeyword(raw, normalized);
    }

    public String exactKey(boolean localized) {
        return localized ? normalized : raw.toLowerCase(Locale.ROOT);
    }

    public String booleanQuery(boolean localized) {
        return localized ? "\"" + normalized + "\"" : legacyQuery();
    }

    public String scoreQuery(boolean localized) {
        return localized ? normalized : legacyQuery();
    }

    private String legacyQuery() {
        return raw.replaceAll("[+\\-<>()~*\"@]", " ").replaceAll("\\s+", " ").trim();
    }
}
