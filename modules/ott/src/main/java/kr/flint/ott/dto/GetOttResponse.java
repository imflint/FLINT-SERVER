package kr.flint.ott.dto;


public record GetOttResponse(
	Long ottId,
	String name,
	String logoUrl
) {
}
