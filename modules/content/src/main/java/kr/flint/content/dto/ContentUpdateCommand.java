package kr.flint.content.dto;

import java.util.List;

public record ContentUpdateCommand(
    String title,
    Integer year,
    String author,
    String description,
    String poster,
    List<String> genreNames
) {
    public static ContentUpdateCommand of(
        String title,
        Integer year,
        String author,
        String description,
        String poster,
        List<String> genreNames
    ) {
        return new ContentUpdateCommand(title, year, author, description, poster, genreNames);
    }
}
