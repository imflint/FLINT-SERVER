package kr.flint.api.domain.bookmark.controller.spec;


import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.api.domain.bookmark.dto.response.GetBookmarkUserRes;

@Schema(description = "컬렉션 북마크 유저 조회 응답")
public class GetBookmarkedUserSwaggerResponse {

	@Schema(description = "성공 코드", example = "SUCCESS_FETCH")
	public String code;

	@Schema(description = "성공 메시지", example = "요청이 성공했습니다.")
	public String message;

	@Schema(description = "북마크 유저 정보")
	public GetBookmarkUserRes data;
}
