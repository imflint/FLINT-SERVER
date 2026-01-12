package kr.flint.api.domain.user.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.user.dto.response.CollectionWithUserProjection;
import kr.flint.api.domain.user.dto.response.UserBookmarkedCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserKeywordsRes;
import kr.flint.api.domain.user.dto.response.UserProfileRes;
import kr.flint.user.domain.User;
import kr.flint.api.domain.user.repository.UserCollectionRepository;
import kr.flint.bookmark.service.BookmarkService;
import kr.flint.taste.dto.response.UserKeywordProjection;
import kr.flint.taste.service.TasteService;
import kr.flint.user.dto.response.NicknameCheckResponse;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryFacade {

    private final UserService userService;
    private final TasteService tasteService;
    private final BookmarkService bookmarkService;
    private final UserCollectionRepository userCollectionRepository;

    public NicknameCheckResponse checkNickname(String nickname) {
        boolean exists = userService.existsByNickname(nickname);
        return NicknameCheckResponse.of(!exists);
    }

    public UserProfileRes getUserProfile(Long userId) {
        User user = userService.getById(userId);
        return UserProfileRes.from(user);
    }

    public UserKeywordsRes getUserKeywords(Long userId) {
        List<UserKeywordProjection> keywords = tasteService.getUserKeywords(userId);
        return UserKeywordsRes.from(keywords);
    }

    // 사용자가 생성한 컬렉션 조회 (본인이면 전체, 타인이면 공개만)
    public UserCollectionsRes getUserCollections(Long userId, boolean isOwner) {
        List<CollectionWithUserProjection> collections = isOwner
            ? userCollectionRepository.findAllCollectionsWithUserByUserId(userId)
            : userCollectionRepository.findPublicCollectionsWithUserByUserId(userId);
        return UserCollectionsRes.from(collections);
    }

    // 사용자가 북마크한 컬렉션 조회 (본인이면 전체, 타인이면 공개만)
    public UserBookmarkedCollectionsRes getUserBookmarkedCollections(Long userId, boolean isOwner) {
        List<Long> collectionIds = bookmarkService.getBookmarkedCollectionIds(userId);
        if (collectionIds.isEmpty()) {
            return UserBookmarkedCollectionsRes.from(Collections.emptyList());
        }
        List<CollectionWithUserProjection> collections = isOwner
            ? userCollectionRepository.findAllCollectionsWithUserByIdIn(collectionIds)
            : userCollectionRepository.findPublicCollectionsWithUserByIdIn(collectionIds);
        return UserBookmarkedCollectionsRes.from(collections);
    }
}
