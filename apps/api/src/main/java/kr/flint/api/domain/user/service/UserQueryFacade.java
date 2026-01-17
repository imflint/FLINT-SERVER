package kr.flint.api.domain.user.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.bookmark.repository.BookmarkQueryRepository;
import kr.flint.api.domain.user.dto.response.CollectionWithUserProjection;
import kr.flint.api.domain.user.dto.response.UserBookmarkedCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserKeywordsRes;
import kr.flint.api.domain.user.dto.response.UserProfileRes;
import kr.flint.infra.gpt.dto.GptKeywordDto;
import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.taste.dto.response.KeywordSimpleRes;
import kr.flint.taste.dto.response.UserKeywordProjection;
import kr.flint.taste.service.TasteService;
import kr.flint.user.domain.User;
import kr.flint.api.domain.user.repository.UserCollectionRepository;
import kr.flint.bookmark.service.BookmarkService;
import kr.flint.user.dto.response.NicknameCheckResponse;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserQueryFacade {

    private final UserService userService;
    private final BookmarkService bookmarkService;
    private final UserCollectionRepository userCollectionRepository;
	private final BookmarkQueryRepository bookmarkQueryRepository;
	private final ChatService chatService;
	private final TasteService tasteService;
	private final UserCommandFacade userCommandFacade;
	private final CloudFrontUrlProvider cloudFrontUrlProvider;

	public NicknameCheckResponse checkNickname(String nickname) {
        boolean exists = userService.existsByNickname(nickname);
        return NicknameCheckResponse.of(!exists);
    }

    public UserProfileRes getUserProfile(Long userId) {
        User user = userService.getById(userId);
        return UserProfileRes.from(user);
    }

	@Transactional
    public UserKeywordsRes getUserKeywords(Long userId) {

		userCommandFacade.callGpt(userId);

        List<UserKeywordProjection> keywords = tasteService.getUserKeywords(userId);
		log.info("keywords: {}", keywords);
        return UserKeywordsRes.from(keywords, cloudFrontUrlProvider::resolveUrl);


		//TODO: 기획한테 언제 취향 키워드 계산할 건지 물어봐야함
    }

    // 사용자가 생성한 컬렉션 조회 (본인이면 전체, 타인이면 공개만)
    public UserCollectionsRes getUserCollections(Long userId, boolean isOwner) {
        List<CollectionWithUserProjection> collections = isOwner
            ? userCollectionRepository.findAllCollectionsWithUserByUserId(userId)
            : userCollectionRepository.findPublicCollectionsWithUserByUserId(userId);
        return UserCollectionsRes.from(collections, cloudFrontUrlProvider::resolveUrl);
    }

    // 사용자가 북마크한 컬렉션 조회 (본인이면 전체, 타인이면 공개만)
    public UserBookmarkedCollectionsRes getUserBookmarkedCollections(Long userId, boolean isOwner) {
        List<Long> collectionIds = bookmarkService.getBookmarkedCollectionIds(userId);
        if (collectionIds.isEmpty()) {
            return UserBookmarkedCollectionsRes.from(Collections.emptyList(), cloudFrontUrlProvider::resolveUrl);
        }
        List<CollectionWithUserProjection> collections = isOwner
            ? userCollectionRepository.findAllCollectionsWithUserByIdIn(collectionIds)
            : userCollectionRepository.findPublicCollectionsWithUserByIdIn(collectionIds);
        return UserBookmarkedCollectionsRes.from(collections, cloudFrontUrlProvider::resolveUrl);
    }
}
