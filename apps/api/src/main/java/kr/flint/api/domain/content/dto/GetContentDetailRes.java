package kr.flint.api.domain.content.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetContentDetailRes(
	@Schema(type = "string")
	Long id,
	String title,
	String imageUrl,
	int year,
	List<GetOttSimpleRes> getOttSimpleList
) {
	public record GetOttSimpleRes(
		String ottName,
		String logoUrl
	) {
	}
}
