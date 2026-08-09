package kr.flint.api.domain.collection.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.api.domain.collection.dto.request.CreateCollectionReq;
import kr.flint.api.domain.collection.dto.request.ReportCollectionReq;
import kr.flint.api.domain.collection.dto.request.UpdateCollectionReq;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.service.ContentService;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CollectionCommandFacade {
    private final CollectionService collectionService;
    private final UserService userService;
    private final ContentService contentService;

    @Transactional
    public Long createCollection(final Long userId, final CreateCollectionReq request) {
        userService.getById(userId);
        userService.validateCanUpload(userId);
        contentService.validateContentIdsExist(request.contentList().stream()
            .map(content -> content.contentId())
            .toList());
        String imageUrl = normalizeImageUrl(request.imageUrl());
        Long collectionId = collectionService.createCollection(userId, request.toCommand(), imageUrl);
        return collectionId;
    }

    @Transactional
    public void updateCollection(final Long userId, final Long collectionId, final UpdateCollectionReq request) {
        userService.getById(userId);
        userService.validateCanUpload(userId);
        contentService.validateContentIdsExist(request.contentList().stream()
            .map(content -> content.contentId())
            .toList());
        String imageUrl = normalizeImageUrl(request.imageUrl());
        collectionService.updateCollection(userId, collectionId, request.toCommand(), imageUrl);
    }

    @Transactional
    public void deleteCollection(final Long userId, final Long collectionId) {
        userService.getById(userId);
        collectionService.deleteCollection(userId, collectionId);
    }

    @Transactional
    public Long reportCollection(final Long reporterId, final Long collectionId, final ReportCollectionReq request) {
        userService.getById(reporterId);
        return collectionService.reportCollection(reporterId, collectionId, request.toCommand());
    }

    private String normalizeImageUrl(String imageUrl) {
        return StringUtils.hasText(imageUrl) ? imageUrl : null;
    }
}
