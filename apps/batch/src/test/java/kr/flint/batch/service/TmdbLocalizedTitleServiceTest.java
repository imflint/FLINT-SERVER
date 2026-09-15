package kr.flint.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.flint.batch.service.TmdbLocalizedTitleService.LocalizedTitles;
import kr.flint.infra.tmdb.dto.TmdbTranslationsRes;

class TmdbLocalizedTitleServiceTest {

    private final TmdbLocalizedTitleService service = new TmdbLocalizedTitleService();

    @Test
    @DisplayName("ko-KR과 en-US 번역을 국가 미지정 번역보다 우선")
    void selectsPreferredLocalizedTitles() {
        TmdbTranslationsRes translations = new TmdbTranslationsRes(List.of(
            translation("ko", "US", "기타 한국어"),
            translation("en", "GB", "British title"),
            translation("ko", "KR", "한국 제목"),
            translation("en", "US", "American title")
        ));

        LocalizedTitles result = service.select("ja", "原題", translations);

        assertThat(result.titleKo()).isEqualTo("한국 제목");
        assertThat(result.titleEn()).isEqualTo("American title");
        assertThat(result.eligible()).isTrue();
    }

    @Test
    @DisplayName("번역이 없어도 원어가 영어면 원제를 영어 제목으로 사용")
    void usesEligibleOriginalTitle() {
        LocalizedTitles result = service.select("en", "Original title", null);

        assertThat(result.titleKo()).isNull();
        assertThat(result.titleEn()).isEqualTo("Original title");
    }

    @Test
    @DisplayName("한글과 영문 제목이 모두 없으면 언어 부적격")
    void rejectsUnsupportedLanguage() {
        LocalizedTitles result = service.select("ja", "原題", null);

        assertThat(result.eligible()).isFalse();
    }

    private TmdbTranslationsRes.Translation translation(String language, String country, String title) {
        return new TmdbTranslationsRes.Translation(
            language,
            country,
            new TmdbTranslationsRes.Data(title, null)
        );
    }
}
