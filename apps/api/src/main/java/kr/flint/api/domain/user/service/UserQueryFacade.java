package kr.flint.api.domain.user.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.api.domain.bookmark.repository.BookmarkQueryRepository;
import kr.flint.api.domain.user.dto.response.CollectionContentImageDto;
import kr.flint.api.domain.user.dto.response.CollectionWithUserDto;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserKeywordsRes;
import kr.flint.api.domain.user.dto.response.UserProfileRes;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.taste.dto.response.UserKeywordProjection;
import kr.flint.taste.service.TasteService;
import kr.flint.user.domain.User;
import kr.flint.api.domain.user.repository.UserCollectionRepository;
import kr.flint.bookmark.service.BookmarkQueryService;
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
    private final BookmarkQueryService bookmarkQueryService;
    private final UserCollectionRepository userCollectionRepository;
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
		userService.getById(userId);

		// 키워드가 없을 때만 GPT 호출
		if (!tasteService.hasUserKeywords(userId)) {
			userCommandFacade.callGpt(userId);
		}

        List<UserKeywordProjection> keywords = tasteService.getUserKeywords(userId);
		log.info("keywords: {}", keywords);
        return UserKeywordsRes.from(keywords, cloudFrontUrlProvider::resolveUrl);

		//TODO: 기획한테 언제 취향 키워드 계산할 건지 물어봐야함
    }

    public UserCollectionsRes getMyCollections(Long userId) {
        List<CollectionWithUserDto> collections = userCollectionRepository.findAllCollectionsWithUserByUserId(userId);
        if (collections.isEmpty()) {
            return new UserCollectionsRes(Collections.emptyList());
        }

        List<Long> collectionIds = collections.stream().map(CollectionWithUserDto::id).toList();
        Map<Long, List<String>> contentImagesMap = buildContentImagesMap(collectionIds);
        Set<Long> bookmarkedIds = bookmarkQueryService.getBookmarkedCollectionIdSet(userId);

        return UserCollectionsRes.from(collections, contentImagesMap, bookmarkedIds, cloudFrontUrlProvider::resolveUrl);
    }

    public UserCollectionsRes getPublicCollections(Long currentUserId, Long targetUserId) {
        List<CollectionWithUserDto> collections = userCollectionRepository.findPublicCollectionsWithUserByUserId(targetUserId);
        if (collections.isEmpty()) {
            return new UserCollectionsRes(Collections.emptyList());
        }

        List<Long> collectionIds = collections.stream().map(CollectionWithUserDto::id).toList();
        Map<Long, List<String>> contentImagesMap = buildContentImagesMap(collectionIds);
        Set<Long> bookmarkedIds = bookmarkQueryService.getBookmarkedCollectionIdSet(currentUserId);

        return UserCollectionsRes.from(collections, contentImagesMap, bookmarkedIds, cloudFrontUrlProvider::resolveUrl);
    }

    public UserCollectionsRes getMyBookmarkedCollections(Long userId) {
        List<Long> collectionIds = bookmarkQueryService.getBookmarkedCollectionIds(userId);
        if (collectionIds.isEmpty()) {
            return new UserCollectionsRes(Collections.emptyList());
        }

        List<CollectionWithUserDto> collections = userCollectionRepository.findAllCollectionsWithUserByIdIn(collectionIds);
        Map<Long, List<String>> contentImagesMap = buildContentImagesMap(collectionIds);
        Set<Long> bookmarkedIds = bookmarkQueryService.getBookmarkedCollectionIdSet(userId);

        return UserCollectionsRes.from(collections, contentImagesMap, bookmarkedIds, cloudFrontUrlProvider::resolveUrl);
    }

    public UserCollectionsRes getPublicBookmarkedCollections(Long currentUserId, Long targetUserId) {
        List<Long> collectionIds = bookmarkQueryService.getBookmarkedCollectionIds(targetUserId);
        if (collectionIds.isEmpty()) {
            return new UserCollectionsRes(Collections.emptyList());
        }

        List<CollectionWithUserDto> collections = userCollectionRepository.findPublicCollectionsWithUserByIdIn(collectionIds);
        List<Long> publicCollectionIds = collections.stream().map(CollectionWithUserDto::id).toList();
        Map<Long, List<String>> contentImagesMap = buildContentImagesMap(publicCollectionIds);
        Set<Long> bookmarkedIds = bookmarkQueryService.getBookmarkedCollectionIdSet(currentUserId);

        return UserCollectionsRes.from(collections, contentImagesMap, bookmarkedIds, cloudFrontUrlProvider::resolveUrl);
    }

    private Map<Long, List<String>> buildContentImagesMap(List<Long> collectionIds) {
        List<CollectionContentImageDto> images = userCollectionRepository.findContentImagesByCollectionIds(collectionIds);
        Map<Long, List<String>> map = new LinkedHashMap<>();
        for (CollectionContentImageDto img : images) {
            String image = img.image();
            if (StringUtils.hasText(image)) {
                map.computeIfAbsent(img.collectionId(), k -> new ArrayList<>()).add(image);
            }
        }
        return map;
    }
}
