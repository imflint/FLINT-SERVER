package kr.flint.admin.domain.collection.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.collection.dto.request.AdminCollectionUpdateReq;
import kr.flint.admin.domain.collection.dto.request.AdminCollectionVisibility;
import kr.flint.admin.domain.collection.dto.response.AdminCollectionDetailRes;
import kr.flint.admin.domain.collection.dto.response.AdminCollectionSummaryRes;
import kr.flint.admin.domain.collection.repository.AdminCollectionQueryRepository;
import kr.flint.admin.domain.collection.repository.AdminCollectionQueryRepository.CollectionContentRow;
import kr.flint.admin.domain.collection.repository.AdminCollectionQueryRepository.CollectionDetailRow;
import kr.flint.admin.domain.collection.repository.AdminCollectionQueryRepository.CollectionSummaryRow;
import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.collection.exception.CollectionErrorCode;
import kr.flint.collection.exception.CollectionException;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.domain.Content;
import kr.flint.content.service.ContentService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.shared.dto.PaginationResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCollectionFacade {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminCollectionQueryRepository queryRepository;
    private final CollectionService collectionService;
    private final ContentService contentService;
    private final CloudFrontUrlProvider cloudFrontUrlProvider;

    public PaginationResponse<AdminCollectionSummaryRes> getCollections(
        Long adminId,
        String keyword,
        AdminCollectionVisibility visibility,
        CollectionModerationStatus moderationStatus,
        Boolean deleted,
        Integer page,
        Integer size
    ) {
        adminAuthorizationService.validateAdmin(adminId);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        List<Long> pageIds = queryRepository.findCollectionIds(keyword, visibility, moderationStatus, deleted, safePage, safeSize);
        long totalElements = queryRepository.countCollections(keyword, visibility, moderationStatus, deleted);

        Map<Long, CollectionSummaryRow> rows = queryRepository.findCollectionSummaryRows(pageIds)
            .stream()
            .collect(Collectors.toMap(CollectionSummaryRow::collectionId, Function.identity()));
        List<AdminCollectionSummaryRes> data = pageIds.stream()
            .map(rows::get)
            .filter(row -> row != null)
            .map(this::toSummary)
            .toList();

        return PaginationResponse.ofOffset(data, safePage, safeSize, totalElements);
    }

    public AdminCollectionDetailRes getCollection(Long adminId, Long collectionId) {
        adminAuthorizationService.validateAdmin(adminId);
        return getCollectionDetail(collectionId);
    }

    @Transactional
    public AdminCollectionDetailRes updateCollection(Long adminId, Long collectionId, AdminCollectionUpdateReq request) {
        adminAuthorizationService.validateAdmin(adminId);
        contentService.validateContentIdsExist(request.contentIds());

        Long firstContentId = request.contentList().getFirst().contentId();
        Content firstContent = contentService.getContentById(firstContentId);
        String imageUrl = StringUtils.hasText(request.imageUrl()) ? request.imageUrl() : firstContent.getPoster();
        collectionService.updateCollectionByAdmin(collectionId, request.toCommand(), imageUrl);
        return getCollectionDetail(collectionId);
    }

    private AdminCollectionDetailRes getCollectionDetail(Long collectionId) {
        CollectionDetailRow row = queryRepository.findCollectionDetailRow(collectionId);
        if (row == null) {
            throw new CollectionException(CollectionErrorCode.COLLECTION_NOT_FOUND);
        }
        List<AdminCollectionDetailRes.ContentInfo> contents = queryRepository.findCollectionContentRows(collectionId)
            .stream()
            .map(this::toContentInfo)
            .toList();

        return new AdminCollectionDetailRes(
            row.collectionId(),
            row.title(),
            row.description(),
            resolveNullableImage(row.image()),
            row.isPublic(),
            row.moderationStatus(),
            row.deletedAt(),
            row.bookmarkCount(),
            row.createdAt(),
            new AdminCollectionDetailRes.OwnerInfo(
                row.ownerId(),
                row.ownerNickname(),
                resolveNullableImage(row.ownerProfileImage())
            ),
            contents
        );
    }

    private AdminCollectionSummaryRes toSummary(CollectionSummaryRow row) {
        return new AdminCollectionSummaryRes(
            row.collectionId(),
            row.title(),
            row.description(),
            resolveNullableImage(row.image()),
            row.isPublic(),
            row.moderationStatus(),
            row.deletedAt(),
            row.bookmarkCount(),
            row.ownerId(),
            row.ownerNickname(),
            row.contentCount() != null ? row.contentCount().intValue() : 0,
            row.createdAt()
        );
    }

    private AdminCollectionDetailRes.ContentInfo toContentInfo(CollectionContentRow row) {
        return new AdminCollectionDetailRes.ContentInfo(
            row.contentId(),
            row.title(),
            resolveNullableImage(row.poster()),
            row.customImages().stream()
                .map(cloudFrontUrlProvider::resolveUrl)
                .toList(),
            row.isSpoiler(),
            row.reason(),
            row.year(),
            row.mediaType()
        );
    }

    private String resolveNullableImage(String imageUrl) {
        return imageUrl == null ? null : cloudFrontUrlProvider.resolveUrl(imageUrl);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }
}
