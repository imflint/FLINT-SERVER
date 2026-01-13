package kr.flint.api.domain.bookmark.controller.spec;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 토글 성공 응답")
public class BookmarkToggleSwaggerResponse {

	@Schema(description = "성공 코드", example = "SUCCESS_UPDATE")
	public String code;

	@Schema(description = "성공 메시지", example = "요청이 성공했습니다.")
	public String message;

	@Schema(description = "북마크 여부", example = "true")
	public Boolean data;
}
