package kr.flint.infra.gpt.dto;

import java.util.List;

public record GptKeywordDto(
	List<TasteKeywords> tasteKeywords
) {
	public record TasteKeywords(
		int rank,
		String level,
		String category,
		String keyword,
		int percent
	){}
}
