package kr.flint.api.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import kr.flint.api.domain.auth.dto.request.SignupReq;
import kr.flint.api.global.oauth.client.KakaoOAuthClient;
import kr.flint.auth.dto.AuthTokens;
import kr.flint.auth.dto.TempTokenPayload;
import kr.flint.auth.enums.AuthProvider;
import kr.flint.auth.service.AuthService;
import kr.flint.auth.service.UserIdentityService;
import kr.flint.bookmark.service.BookmarkCommandService;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.service.ContentService;
import kr.flint.ott.service.OttService;
import kr.flint.taste.service.TasteService;
import kr.flint.terms.exception.TermsErrorCode;
import kr.flint.terms.exception.TermsException;
import kr.flint.terms.service.TermsService;
import kr.flint.user.dto.response.UserAuthInfo;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class AuthFacadeTest {

	@Mock
	private AuthService authService;

	@Mock
	private UserService userService;

	@Mock
	private UserIdentityService userIdentityService;

	@Mock
	private KakaoOAuthClient kakaoOAuthClient;

	@Mock
	private BookmarkCommandService bookmarkCommandService;

	@Mock
	private OttService ottService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private CollectionService collectionService;

	@Mock
	private TasteService tasteService;

	@Mock
	private ContentService contentService;

	@Mock
	private TermsService termsService;

	@InjectMocks
	private AuthFacade authFacade;

	@Nested
	@DisplayName("signup")
	class Signup {

		@Test
		@DisplayName("회원가입 시 약관 동의를 저장")
		void createTermsAgreements() {
			// given
			SignupReq request = new SignupReq(
				"temp-token",
				"플린트",
				List.of(100L),
				List.of(1L),
				List.of(10L, 20L)
			);
			when(authService.verifyTempToken("temp-token"))
				.thenReturn(new TempTokenPayload(AuthProvider.KAKAO, "provider-user-id"));
			when(userService.create("플린트")).thenReturn(UserAuthInfo.of(1L, "플린트", "FLING"));
			when(authService.issueTokens(1L, "FLING")).thenReturn(AuthTokens.of("access", "refresh", 1L));

			// when
			var result = authFacade.signup(request);

			// then
			assertThat(result.accessToken()).isEqualTo("access");
			verify(termsService).validateAndCreateAgreements(1L, List.of(10L, 20L));
		}

		@Test
		@DisplayName("필수 약관 미동의 시 온보딩 후속 처리를 하지 않음")
		void requiredTermsNotAgreed() {
			// given
			SignupReq request = new SignupReq(
				"temp-token",
				"플린트",
				List.of(100L),
				List.of(1L),
				List.of(10L)
			);
			when(authService.verifyTempToken("temp-token"))
				.thenReturn(new TempTokenPayload(AuthProvider.KAKAO, "provider-user-id"));
			when(userService.create("플린트")).thenReturn(UserAuthInfo.of(1L, "플린트", "FLING"));
			doThrow(new TermsException(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED))
				.when(termsService).validateAndCreateAgreements(1L, List.of(10L));

			// when & then
			assertThatThrownBy(() -> authFacade.signup(request))
				.isInstanceOf(TermsException.class)
				.extracting("errorCode")
				.isEqualTo(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
			verify(bookmarkCommandService, never()).createContentBookmarks(1L, List.of(100L));
			verify(ottService, never()).createUserOtts(1L, List.of(1L));
			verify(authService, never()).issueTokens(1L, "FLING");
		}
	}
}
