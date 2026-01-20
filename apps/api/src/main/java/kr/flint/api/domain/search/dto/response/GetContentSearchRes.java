package kr.flint.api.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.content.domain.Content;

public record GetContentSearchRes(
	@Schema(type = "string")
	Long id,
	String title,
	String author,
	String posterUrl,
	int year
) {
	public static GetContentSearchRes of(Long id, String title, String author, String posterUrl, int year) {
		return new GetContentSearchRes(id, title, author, posterUrl, year);
	}

	public static GetContentSearchRes from(Content content) {
		return new GetContentSearchRes(
			content.getId(),
			content.getTitle(),
			content.getAuthor(),
			content.getPoster(),
			content.getYear()
		);
	}
}
