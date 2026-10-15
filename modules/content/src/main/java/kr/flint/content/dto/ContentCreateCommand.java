package kr.flint.content.dto;

import java.util.List;

import kr.flint.content.domain.MediaType;

public record ContentCreateCommand(
    MediaType mediaType,
    String title,
    int year,
    String author,
    String description,
    String poster,
    List<String> genreNames
) {
    public static ContentCreateCommand of(
        MediaType mediaType,
        String title,
        int year,
        String author,
        String description,
        String poster,
        List<String> genreNames
    ) {
        return new ContentCreateCommand(mediaType, title, year, author, description, poster,
            genreNames == null ? List.of() : List.copyOf(genreNames));
    }
}
