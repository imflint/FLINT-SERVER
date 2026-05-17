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
import kr.flint.shared.dto.SliceCursor;
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
        Long cursor,
        Integer size
    ) {
        adminAuthorizationService.validateAdmin(adminId);
        int safeSize = normalizeSize(size);
        List<Long> collectionIds = queryRepository.findCollectionIds(keyword, visibility, moderationStatus, cursor, safeSize);
        boolean hasNext = collectionIds.size() > safeSize;
        List<Long> pageIds = hasNext ? collectionIds.subList(0, safeSize) : collectionIds;

        Map<Long, CollectionSummaryRow> rows = queryRepository.findCollectionSummaryRows(pageIds)
            .stream()
            .collect(Collectors.toMap(CollectionSummaryRow::collectionId, Function.identity()));
        List<AdminCollectionSummaryRes> data = pageIds.stream()
            .map(rows::get)
            .filter(row -> row != null)
            .map(this::toSummary)
            .toList();

        String nextCursor = hasNext && !data.isEmpty() ? String.valueOf(data.getLast().collectionId()) : "";
        String currentCursor = cursor != null ? String.valueOf(cursor) : null;
        return PaginationResponse.ofCursor(SliceCursor.of(data, currentCursor, nextCursor));
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
            resolveNullableImage(row.customImage()),
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
}
