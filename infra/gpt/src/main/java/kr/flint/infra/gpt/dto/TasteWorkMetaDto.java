package kr.flint.infra.gpt.dto;

import java.util.List;

public record TasteWorkMetaDto(
	Long contentId,
	String title,
	List<String> genreList,
	String overview
) {
}
