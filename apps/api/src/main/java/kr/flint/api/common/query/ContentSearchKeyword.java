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
        if (normalized == null) {
            throw new GeneralException(ErrorCode.INVALID_INPUT, "keyword는 문자 또는 숫자를 1자 이상 포함해야 합니다.");
        }
        return new ContentSearchKeyword(raw, normalized);
    }

    public String exactKey(boolean localized) {
        return localized ? normalized : raw.toLowerCase(Locale.ROOT);
    }

    public boolean isSingleCharacter() {
        return normalized.codePointCount(0, normalized.length()) == 1;
    }

    public String wordKey() {
        return ContentTitleNormalizer.normalizeWordsNullable(raw);
    }

    public boolean usesEnglishWords(boolean enabled) {
        String words = wordKey();
        return enabled && words != null && words.matches("[a-z0-9 ]+") && words.matches(".*[a-z].*");
    }

    public String wordPhraseQuery() {
        return "\"" + wordKey() + "\"";
    }

    public String booleanQuery(boolean localized) {
        if (!localized) {
            return legacyQuery();
        }
        int[] points = normalized.codePoints().toArray();
        StringBuilder query = new StringBuilder();
        // Repeated bigrams retain their contribution to the original relevance score.
        for (int i = 0; i < points.length - 1; i++) {
            if (!query.isEmpty()) {
                query.append(' ');
            }
            query.append('+').appendCodePoint(points[i]).appendCodePoint(points[i + 1]);
        }
        return query.toString();
    }

    public String scoreQuery(boolean localized) {
        return localized ? normalized : legacyQuery();
    }

    private String legacyQuery() {
        return raw.replaceAll("[+\\-<>()~*\"@]", " ").replaceAll("\\s+", " ").trim();
    }
}
