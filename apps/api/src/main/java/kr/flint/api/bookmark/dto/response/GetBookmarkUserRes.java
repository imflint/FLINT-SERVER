package kr.flint.api.bookmark.dto.response;

import java.util.List;

import kr.flint.user.dto.response.UserSimpleRes;

public record GetBookmarkUserRes(
	int bookmarkCount,
	List<UserSimpleRes> userList
) {
	public static GetBookmarkUserRes of(int bookmarkCount, List<UserSimpleRes> userList) {
		return new GetBookmarkUserRes(bookmarkCount, userList);
	}
}
