package kr.flint.api.domain.content.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetContentDetailRes(
	Long id,
	String title,
	@JsonInclude(JsonInclude.Include.ALWAYS)
	@Schema(description = "영화 감독 또는 TV creator. 정보가 없으면 null", nullable = true, example = "봉준호")
	String author,
	String imageUrl,
	int year,
	int bookmarkCount,
	List<GetOttSimpleRes> getOttSimpleList
) {
	public record GetOttSimpleRes(
		String ottName,
		String logoUrl
	) {
	}
}
