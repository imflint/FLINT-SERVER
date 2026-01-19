package kr.flint.api.domain.content.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetContentDetailRes(
	Long id,
	String title,
	int year,
	List<GetOttSimpleRes> getOttSimpleList
) {
	public record GetOttSimpleRes(
		String ottName,
		String logoUrl
	) {
	}
}
