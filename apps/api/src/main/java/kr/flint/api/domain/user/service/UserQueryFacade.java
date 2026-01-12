package kr.flint.api.domain.user.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.user.dto.response.CollectionWithUserProjection;
import kr.flint.api.domain.user.dto.response.UserBookmarkedCollectionsResponse;
import kr.flint.api.domain.user.dto.response.UserCollectionsResponse;
import kr.flint.api.domain.user.dto.response.UserKeywordsResponse;
import kr.flint.api.domain.user.repository.UserCollectionRepository;
import kr.flint.bookmark.repository.CollectionBookmarkRepository;
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
    private final UserCollectionRepository userCollectionRepository;
    private final CollectionBookmarkRepository collectionBookmarkRepository;

    public NicknameCheckResponse checkNickname(String nickname) {
        boolean exists = userService.existsByNickname(nickname);
        return NicknameCheckResponse.of(!exists);
    }

    public UserKeywordsResponse getUserKeywords(Long userId) {
        List<UserKeywordProjection> keywords = tasteService.getUserKeywords(userId);
        return UserKeywordsResponse.from(keywords);
    }

    public UserCollectionsResponse getUserCollections(Long userId) {
        List<CollectionWithUserProjection> collections = userCollectionRepository.findCollectionsWithUserByUserId(userId);
        return UserCollectionsResponse.from(collections);
    }

    public UserBookmarkedCollectionsResponse getUserBookmarkedCollections(Long userId) {
        List<Long> collectionIds = collectionBookmarkRepository.findCollectionIdsByUserId(userId);
        if (collectionIds.isEmpty()) {
            return UserBookmarkedCollectionsResponse.from(Collections.emptyList());
        }
        List<CollectionWithUserProjection> collections = userCollectionRepository.findCollectionsWithUserByIdIn(collectionIds);
        return UserBookmarkedCollectionsResponse.from(collections);
    }
}
