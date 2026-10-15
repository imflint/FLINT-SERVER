package kr.flint.api.admin.domain.content.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;

@Schema(description = "관리자 콘텐츠 응답")
public record AdminContentRes(
    Long id,
    @Schema(description = "TMDB ID. 수동 콘텐츠에는 포함되지 않습니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    Long tmdbId,
    MediaType mediaType,
    String title,
    int year,
    String author,
    String description,
    String posterUrl,
    int bookmarkCount,
    List<String> genreNames
) {
    public static AdminContentRes from(Content content, List<String> genreNames, String posterUrl) {
        return new AdminContentRes(
            content.getId(),
            content.getTmdbId(),
            content.getMediaType(),
            content.getTitle(),
            content.getYear(),
            content.getAuthor(),
            content.getDescription(),
            posterUrl,
            content.getBookmarkCount(),
            genreNames
        );
    }
}
