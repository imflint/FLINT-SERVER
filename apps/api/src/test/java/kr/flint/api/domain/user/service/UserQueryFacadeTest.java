package kr.flint.api.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.api.domain.bookmark.repository.BookmarkQueryRepository;
import kr.flint.api.domain.user.dto.response.CollectionContentImageDto;
import kr.flint.api.domain.user.dto.response.CollectionWithUserDto;
import kr.flint.api.domain.user.dto.response.MyProfileRes;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserKeywordsRes;
import kr.flint.api.domain.user.dto.response.UserProfileRes;
import kr.flint.api.domain.user.repository.UserCollectionRepository;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.taste.service.TasteService;
import kr.flint.taste.domain.KeywordLevel;
import kr.flint.taste.dto.response.UserKeywordProjection;
import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.service.TermsService;
import kr.flint.user.domain.User;
import kr.flint.user.exception.UserErrorCode;
import kr.flint.user.exception.UserException;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserQueryFacadeTest {

    @Mock
    private UserService userService;

    @Mock
    private BookmarkQueryService bookmarkQueryService;

    @Mock
    private UserCollectionRepository userCollectionRepository;

    @Mock
    private BookmarkQueryRepository bookmarkQueryRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private TasteService tasteService;

    @Mock
    private UserCommandFacade userCommandFacade;

    @Mock
    private CloudFrontUrlProvider cloudFrontUrlProvider;

	@Mock
	private TermsService termsService;

    @InjectMocks
    private UserQueryFacade userQueryFacade;

	@Nested
	@DisplayName("getMyProfile")
	class GetMyProfile {

		@Test
		@DisplayName("내 프로필에 필수 약관 추가 동의 상태를 포함")
		void includeTermsAgreementStatus() {
			// given
			User user = createUser(1L, "플린트", "user/profile/profile-key.jpg");
			Terms service = createTerms(10L, TermsType.SERVICE, 2, true);
			when(userService.getById(1L)).thenReturn(user);
			when(termsService.getPendingRequiredTerms(1L, TermsContext.SIGNUP)).thenReturn(List.of(service));
			when(cloudFrontUrlProvider.resolveUrl("user/profile/profile-key.jpg"))
				.thenReturn("https://cdn.flint.kr/user/profile/profile-key.jpg");

			// when
			MyProfileRes response = userQueryFacade.getMyProfile(1L);

			// then
			assertThat(response.id()).isEqualTo("1");
			assertThat(response.profileImageUrl()).isEqualTo("https://cdn.flint.kr/user/profile/profile-key.jpg");
			assertThat(response.termsAgreementStatus().requiredTermsAgreementNeeded()).isTrue();
			assertThat(response.termsAgreementStatus().pendingRequiredTerms())
				.extracting("id")
				.containsExactly(10L);
		}
	}

	@Nested
	@DisplayName("getUserProfile")
	class GetUserProfile {

		@Test
		@DisplayName("공개 프로필 조회는 약관 동의 상태를 계산하지 않음")
		void doesNotCalculateTermsStatus() {
			// given
			User user = createUser(1L, "플린트", "user/profile/profile-key.jpg");
			when(userService.getById(1L)).thenReturn(user);
			when(cloudFrontUrlProvider.resolveUrl("user/profile/profile-key.jpg"))
				.thenReturn("https://cdn.flint.kr/user/profile/profile-key.jpg");

			// when
			UserProfileRes response = userQueryFacade.getUserProfile(1L);

			// then
			assertThat(response.id()).isEqualTo("1");
			assertThat(response.profileImageUrl()).isEqualTo("https://cdn.flint.kr/user/profile/profile-key.jpg");
			verifyNoInteractions(termsService);
		}
	}

	@Nested
	@DisplayName("getUserKeywords")
	class GetUserKeywords {

		@Test
		@DisplayName("존재하지 않는 사용자의 키워드 조회는 실패")
		void userNotFound() {
			// given
			Long userId = 999L;
			when(userService.getById(userId)).thenThrow(new UserException(UserErrorCode.USER_NOT_FOUND));

			// when & then
			assertThatThrownBy(() -> userQueryFacade.getUserKeywords(userId))
				.isInstanceOf(UserException.class);
			verifyNoInteractions(tasteService, userCommandFacade);
		}

		@Test
		@DisplayName("저장된 순위가 동점이어도 응답은 코어 3개와 서브 3개로 구분 가능한 순위를 반환")
		void normalizeDuplicateRanksInResponse() {
			Long userId = 1L;
			when(userService.getById(userId)).thenReturn(createUser(userId, "플린트"));
			when(tasteService.hasUserKeywords(userId)).thenReturn(true);
			when(tasteService.getUserKeywords(userId)).thenReturn(List.of(
				keywordProjection(1, "드라마"),
				keywordProjection(2, "모험"),
				keywordProjection(2, "범죄"),
				keywordProjection(3, "액션"),
				keywordProjection(4, "로맨스"),
				keywordProjection(5, "코미디")
			));

			UserKeywordsRes response = userQueryFacade.getUserKeywords(userId);

			assertThat(response.keywords())
				.extracting(UserKeywordsRes.KeywordItem::rank)
				.containsExactly(1, 2, 3, 4, 5, 6);
		}
	}

    @Nested
    @DisplayName("getMyCollections")
    class GetMyCollections {

        @Test
        @DisplayName("콘텐츠 이미지가 null이면 사용자 컬렉션 이미지 목록에서 제외")
        void excludeNullContentImage() {
            // given
            Long userId = 1L;
            Long collectionId = 10L;
            when(userCollectionRepository.findAllCollectionsWithUserByUserId(userId))
                .thenReturn(List.of(new CollectionWithUserDto(
                    collectionId,
                    "컬렉션 제목",
                    "컬렉션 설명",
                    "collection.jpg",
                    3,
                    userId,
                    "profile.jpg",
                    "플린트"
                )));
            when(userCollectionRepository.findContentImagesByCollectionIds(List.of(collectionId)))
                .thenReturn(List.of(
                    new CollectionContentImageDto(collectionId, null),
                    new CollectionContentImageDto(collectionId, "poster.jpg")
                ));
            when(bookmarkQueryService.getBookmarkedCollectionIdSet(userId)).thenReturn(Set.of());
            when(cloudFrontUrlProvider.resolveUrl(nullable(String.class)))
                .thenAnswer(invocation -> {
                    String imageUrl = invocation.getArgument(0, String.class);
                    if (imageUrl == null || imageUrl.isBlank()) {
                        throw new NullPointerException("image key must not be blank");
                    }
                    return "resolved/" + imageUrl;
                });

            // when
            UserCollectionsRes response = userQueryFacade.getMyCollections(userId);

            // then
            assertThat(response.collections().getFirst().imageList()).containsExactly("resolved/poster.jpg");
            verify(cloudFrontUrlProvider, never()).resolveUrl(isNull());
        }
    }

	private User createUser(Long id, String nickname) {
		return createUser(id, nickname, null);
	}

	private User createUser(Long id, String nickname, String profileImage) {
		User user = User.createFling(nickname, profileImage);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Terms createTerms(Long id, TermsType type, Integer version, boolean required) {
		Terms terms = Terms.create(TermsContext.SIGNUP, type, version, type.getDescription(), "content", required,
			java.time.LocalDateTime.now().minusDays(1));
		ReflectionTestUtils.setField(terms, "id", id);
		return terms;
	}

	private UserKeywordProjection keywordProjection(int rank, String name) {
		return new UserKeywordProjection() {
			@Override
			public int getRanking() {
				return rank;
			}

			@Override
			public String getImageUrl() {
				return null;
			}

			@Override
			public KeywordLevel getLevel() {
				return KeywordLevel.LV1;
			}

			@Override
			public String getName() {
				return name;
			}

			@Override
			public Integer getPercentage() {
				return 50;
			}
		};
	}
}
