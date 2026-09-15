package kr.flint.infra.tmdb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TmdbTranslationsRes(
    List<Translation> translations
) {
    public record Translation(
        @JsonProperty("iso_639_1") String language,
        @JsonProperty("iso_3166_1") String country,
        Data data
    ) {
    }

    public record Data(
        String title,
        String name
    ) {
        public String localizedTitle() {
            return title != null && !title.isBlank() ? title : name;
        }
    }
}
