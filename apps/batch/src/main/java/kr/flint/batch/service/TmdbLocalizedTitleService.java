package kr.flint.batch.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import kr.flint.infra.tmdb.dto.TmdbTranslationsRes;

@Service
public class TmdbLocalizedTitleService {

    public LocalizedTitles select(
        String originalLanguage,
        String originalTitle,
        TmdbTranslationsRes translations
    ) {
        List<TmdbTranslationsRes.Translation> items = translations == null || translations.translations() == null
            ? List.of()
            : translations.translations();

        String titleKo = selectTranslation(items, "ko", List.of("KR"));
        String titleEn = selectTranslation(items, "en", List.of("US", "GB"));

        if (titleKo == null && "ko".equalsIgnoreCase(originalLanguage)) {
            titleKo = trimNullable(originalTitle);
        }
        if (titleEn == null && "en".equalsIgnoreCase(originalLanguage)) {
            titleEn = trimNullable(originalTitle);
        }
        return new LocalizedTitles(titleKo, titleEn);
    }

    private String selectTranslation(
        List<TmdbTranslationsRes.Translation> translations,
        String language,
        List<String> preferredCountries
    ) {
        return translations.stream()
            .filter(translation -> language.equalsIgnoreCase(translation.language()))
            .filter(translation -> translation.data() != null)
            .filter(translation -> StringUtils.hasText(translation.data().localizedTitle()))
            .sorted(Comparator.comparingInt(translation -> countryPriority(translation.country(), preferredCountries)))
            .map(translation -> translation.data().localizedTitle().trim())
            .findFirst()
            .orElse(null);
    }

    private int countryPriority(String country, List<String> preferredCountries) {
        for (int index = 0; index < preferredCountries.size(); index++) {
            if (preferredCountries.get(index).equalsIgnoreCase(country)) {
                return index;
            }
        }
        return preferredCountries.size();
    }

    private String trimNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record LocalizedTitles(String titleKo, String titleEn) {
        public boolean eligible() {
            return titleKo != null || titleEn != null;
        }
    }
}
