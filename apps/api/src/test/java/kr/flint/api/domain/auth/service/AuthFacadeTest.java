package kr.flint.api.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import kr.flint.api.domain.auth.dto.request.SocialVerifyReq;
import kr.flint.api.global.oauth.client.AppleOAuthClient;
import kr.flint.api.global.oauth.client.KakaoOAuthClient;
import kr.flint.auth.enums.AuthProvider;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.service.AuthService;
import kr.flint.auth.service.UserIdentityService;
import kr.flint.bookmark.service.BookmarkCommandService;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.service.ContentService;
import kr.flint.ott.service.OttService;
import kr.flint.taste.service.TasteService;
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
    private AppleOAuthClient appleOAuthClient;

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

    @InjectMocks
    private AuthFacade authFacade;

    @Nested
    @DisplayName("verifySocialCode")
    class VerifySocialCode {

        @Test
        @DisplayName("Apple authorization code 플로우는 지원하지 않는다")
        void appleAuthorizationCodeUnsupported() {
            // given
            SocialVerifyReq request = new SocialVerifyReq(AuthProvider.APPLE, "authorization-code", null);

            // when & then
            assertThatThrownBy(() -> authFacade.verifySocialCode(request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.UNSUPPORTED_SOCIAL_FLOW);
            verify(appleOAuthClient, never()).getUserInfoByCode("authorization-code");
            verify(appleOAuthClient, never()).getUserInfoByIdentityToken(null);
        }
    }
}
