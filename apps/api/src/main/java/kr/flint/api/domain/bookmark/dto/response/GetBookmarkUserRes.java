package kr.flint.api.domain.bookmark.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.user.dto.response.UserSimpleRes;

@Schema(description = "컬렉션 북마크 유저 목록 응답")
public record GetBookmarkUserRes(
	@Schema(description = "총 북마크 수", example = "42")
	int bookmarkCount,
	@ArraySchema(schema = @Schema(implementation = UserSimpleRes.class))
	List<UserSimpleRes> userList
) {
	public static GetBookmarkUserRes of(int bookmarkCount, List<UserSimpleRes> userList) {
		return new GetBookmarkUserRes(bookmarkCount, userList);
	}
}
