package kr.flint.content.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.stream.Stream;

import org.springframework.util.StringUtils;

public final class ContentTitleNormalizer {

    private ContentTitleNormalizer() {
    }

    public static String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]", "");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    public static String buildSearchTitle(String titleKo, String titleEn) {
        return Stream.of(
                trimNullable(titleKo),
                normalizeNullable(titleKo),
                trimNullable(titleEn),
                normalizeNullable(titleEn)
            )
            .filter(StringUtils::hasText)
            .distinct()
            .reduce((left, right) -> left + " " + right)
            .orElse(null);
    }

    public static String displayTitle(String titleKo, String titleEn) {
        String korean = trimNullable(titleKo);
        return korean != null ? korean : trimNullable(titleEn);
    }

    private static String trimNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
