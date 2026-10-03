package kr.flint.api.domain.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.domain.bookmark.dto.response.GetBookmarkUserRes;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.infra.storage.cloudfront.properties.CloudFrontProperties;
import kr.flint.user.dto.response.UserSimpleRes;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class BookmarkQueryFacadeTest {

	@Mock
	private UserService userService;

	@Mock
	private BookmarkQueryService bookmarkQueryService;

	@Spy
	private CloudFrontUrlProvider cloudFrontUrlProvider =
		new CloudFrontUrlProvider(new CloudFrontProperties("https://cdn.flint.kr", true));

	@InjectMocks
	private BookmarkQueryFacade bookmarkQueryFacade;

	@ParameterizedTest
	@MethodSource("profileImageCases")
	@DisplayName("저장 사용자의 프로필 이미지 key는 URL로 변환하고 외부 URL과 이미지 부재를 처리")
	void resolveBookmarkedUserProfileImage(String profileImage, String expectedUrl) {
		Long collectionId = 1L;
		List<Long> userIds = List.of(10L, 20L);
		when(bookmarkQueryService.getBookmarkCount(collectionId)).thenReturn(2);
		when(bookmarkQueryService.getBookmarkUserId(collectionId)).thenReturn(userIds);
		when(userService.getUserInfoList(userIds)).thenReturn(List.of(
			new UserSimpleRes(10L, "플린트", profileImage, "FLING"),
			new UserSimpleRes(20L, "작성자", "user/profile/another.png", "FLINER")
		));

		GetBookmarkUserRes response = bookmarkQueryFacade.getBookmarkedUser(collectionId);

		assertThat(response.bookmarkCount()).isEqualTo(2);
		assertThat(response.userList()).containsExactly(
			new UserSimpleRes(10L, "플린트", expectedUrl, "FLING"),
			new UserSimpleRes(20L, "작성자", "https://cdn.flint.kr/user/profile/another.png", "FLINER")
		);
	}

	@Test
	@DisplayName("저장 사용자가 없으면 사용자 조회 없이 빈 목록을 반환")
	void noBookmarkedUsers() {
		Long collectionId = 1L;
		when(bookmarkQueryService.getBookmarkCount(collectionId)).thenReturn(0);
		when(bookmarkQueryService.getBookmarkUserId(collectionId)).thenReturn(List.of());

		GetBookmarkUserRes response = bookmarkQueryFacade.getBookmarkedUser(collectionId);

		assertThat(response.bookmarkCount()).isZero();
		assertThat(response.userList()).isEmpty();
		verify(userService, never()).getUserInfoList(anyList());
	}

	static Stream<Arguments> profileImageCases() {
		return Stream.of(
			Arguments.of("user/profile/profile.png", "https://cdn.flint.kr/user/profile/profile.png"),
			Arguments.of("https://example.com/profile.png", "https://example.com/profile.png"),
			Arguments.of("http://example.com/profile.png", "http://example.com/profile.png"),
			Arguments.of(null, null),
			Arguments.of("   ", null)
		);
	}
}
