package kr.flint.content.dto;

import java.util.List;

import kr.flint.content.domain.Content;

public record ContentWithGenres(
    Long contentId,
    String title,
    List<String> genreList,
    String overview
) {
    public static ContentWithGenres of(Content content, List<String> genreNames) {
        return new ContentWithGenres(
            content.getId(),
            content.getTitle(),
            genreNames,
            content.getDescription()
        );
    }
}
