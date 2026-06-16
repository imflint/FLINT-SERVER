package kr.flint.collection.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.hypersistence.tsid.TSID;
import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContent;
import kr.flint.collection.domain.CollectionContentImage;
import kr.flint.collection.domain.CollectionReport;
import kr.flint.collection.domain.ReportReason;
import kr.flint.collection.dto.CollectionCreateCommand;
import kr.flint.collection.dto.CollectionCreateCommand.ContentInput;
import kr.flint.collection.dto.CollectionUpdateCommand;
import kr.flint.collection.dto.ReportCollectionCommand;
import kr.flint.collection.event.CollectionContentAddedEvent;
import kr.flint.collection.event.CollectionContentRemovedEvent;
import kr.flint.collection.event.CollectionReportedEvent;
import kr.flint.collection.exception.CollectionErrorCode;
import kr.flint.collection.exception.CollectionException;
import kr.flint.collection.repository.CollectionContentRepository;
import kr.flint.collection.repository.CollectionContentImageRepository;
import kr.flint.collection.repository.CollectionReportRepository;
import kr.flint.collection.repository.CollectionRepository;
import kr.flint.collection.repository.RecentViewedCollectionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {
    private final CollectionRepository collectionRepository;
    private final CollectionContentRepository collectionContentRepository;
    private final CollectionContentImageRepository collectionContentImageRepository;
    private final CollectionReportRepository collectionReportRepository;
    private final RecentViewedCollectionRepository recentViewedCollectionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long createCollection(final Long userId, final CollectionCreateCommand command, final String contentUrl) {
        Collection newCollection = Collection.create(
            command.title(),
            command.description(),
            contentUrl,
            command.isPublic(),
            userId
        );

        Collection savedCollection = collectionRepository.save(newCollection);

        List<CollectionContent> collectionContentList = createCollectionContents(savedCollection, command.contents());

        collectionContentRepository.saveAll(collectionContentList);
        saveContentImages(collectionContentList, command.contents());

        // 콘텐츠 추가 이벤트 발행 (CollectionKeyword 동기화 트리거)
        collectionContentList.forEach(content ->
            eventPublisher.publishEvent(new CollectionContentAddedEvent(savedCollection.getId(), content.getContentId()))
        );

        return savedCollection.getId();
    }

    @Transactional
    public void updateCollection(
        final Long userId,
        final Long collectionId,
        final CollectionUpdateCommand command,
        final String coverImageUrl
    ) {
        Collection collection = collectionRepository.findById(collectionId)
            .orElseThrow(() -> new CollectionException(CollectionErrorCode.COLLECTION_NOT_FOUND));

        if (!collection.isOwnedBy(userId)) {
            throw new CollectionException(CollectionErrorCode.COLLECTION_FORBIDDEN);
        }

        replaceCollection(collection, collectionId, command, coverImageUrl);
    }

    @Transactional
    public void updateCollectionByAdmin(
        final Long collectionId,
        final CollectionUpdateCommand command,
        final String coverImageUrl
    ) {
        Collection collection = collectionRepository.findById(collectionId)
            .orElseThrow(() -> new CollectionException(CollectionErrorCode.COLLECTION_NOT_FOUND));

        replaceCollection(collection, collectionId, command, coverImageUrl);
    }

    private void replaceCollection(
        Collection collection,
        Long collectionId,
        CollectionUpdateCommand command,
        String coverImageUrl
    ) {
        collection.update(command.title(), command.description(), coverImageUrl, command.isPublic());

        // 작품 리스트 replace 전략 (단순/원자적). 이벤트는 실제 add/remove diff에 대해서만 발행.
        Set<Long> existingContentIds = new HashSet<>(collectionContentRepository.findContentIdsByCollectionId(collectionId));
        Set<Long> newContentIds = command.contents().stream()
            .map(ContentInput::contentId)
            .collect(java.util.stream.Collectors.toSet());

        collectionContentImageRepository.deleteAllByCollectionContentCollection(collection);
        collectionContentRepository.deleteAllByCollection(collection);
        // delete를 즉시 DB에 반영해야 직후 saveAll 시 unique(collection_id, content_id) 충돌이 안 난다.
        collectionContentRepository.flush();

        List<CollectionContent> rebuilt = createCollectionContents(collection, command.contents());
        collectionContentRepository.saveAll(rebuilt);
        saveContentImages(rebuilt, command.contents());

        Set<Long> added = new HashSet<>(newContentIds);
        added.removeAll(existingContentIds);
        Set<Long> removed = new HashSet<>(existingContentIds);
        removed.removeAll(newContentIds);

        added.forEach(contentId ->
            eventPublisher.publishEvent(new CollectionContentAddedEvent(collectionId, contentId))
        );
        removed.forEach(contentId ->
            eventPublisher.publishEvent(new CollectionContentRemovedEvent(collectionId, contentId))
        );
    }

    private void saveContentImages(
        List<CollectionContent> collectionContents,
        List<ContentInput> contentInputs
    ) {
        List<CollectionContentImage> images = new ArrayList<>();
        for (int contentIndex = 0; contentIndex < collectionContents.size(); contentIndex++) {
            CollectionContent collectionContent = collectionContents.get(contentIndex);
            List<String> customImages = contentInputs.get(contentIndex).customImages();
            int sortOrder = 0;
            for (int imageIndex = 0; imageIndex < customImages.size(); imageIndex++) {
                String imageKey = customImages.get(imageIndex);
                if (imageKey == null || imageKey.isBlank()) {
                    continue;
                }
                images.add(CollectionContentImage.create(collectionContent, imageKey, sortOrder++));
            }
        }
        if (!images.isEmpty()) {
            collectionContentImageRepository.saveAll(images);
        }
    }

    private List<CollectionContent> createCollectionContents(
        Collection collection,
        List<ContentInput> contentInputs
    ) {
        List<CollectionContent> collectionContents = new ArrayList<>();
        for (int sortOrder = 0; sortOrder < contentInputs.size(); sortOrder++) {
            ContentInput content = contentInputs.get(sortOrder);
            collectionContents.add(CollectionContent.create(
                collection,
                content.contentId(),
                content.isSpoiler(),
                content.reason(),
                sortOrder
            ));
        }
        return collectionContents;
    }

    public Collection getCollectionById(final Long collectionId) {
        return collectionRepository.findById(collectionId)
            .orElseThrow(() -> new CollectionException(CollectionErrorCode.COLLECTION_NOT_FOUND));
    }

    public CollectionReport getReportById(final Long reportId) {
        return collectionReportRepository.findById(reportId)
            .orElseThrow(() -> new CollectionException(CollectionErrorCode.COLLECTION_REPORT_NOT_FOUND));
    }

    @Transactional
    public Long reportCollection(final Long reporterId, final Long collectionId, final ReportCollectionCommand command) {
        // 컬렉션 존재 확인 (없는 컬렉션은 신고할 수 없음 → 404)
        getCollectionById(collectionId);

        CollectionReport report = collectionReportRepository.save(
            CollectionReport.create(reporterId, collectionId, command.reasons(), command.otherDetail())
        );

        List<String> reasonLabels = command.reasons().stream()
            .map(ReportReason::label)
            .toList();
        eventPublisher.publishEvent(new CollectionReportedEvent(
            report.getId(),
            reporterId,
            collectionId,
            reasonLabels,
            command.otherDetail()
        ));
        return report.getId();
    }

    @Transactional
    public void increaseBookmarkCount(final Long collectionId){
        Collection collection = getCollectionById(collectionId);
        collection.increaseBookmarkCount();
    }

    @Transactional
    public void decreaseBookmarkCount(final Long collectionId){
        Collection collection = getCollectionById(collectionId);
        collection.decreaseBookmarkCount();
    }

    @Transactional
    public void hideByAdmin(Long collectionId) {
        getCollectionById(collectionId).hideByAdmin();
    }

    @Transactional
    public void deleteByAdmin(Long collectionId) {
        getCollectionById(collectionId).deleteByAdmin();
    }

    @Transactional
    public void resolveReport(
        Long reportId,
        LocalDateTime processedAt
    ) {
        CollectionReport report = getReportById(reportId);
        if (report.isResolved()) {
            throw new CollectionException(CollectionErrorCode.COLLECTION_REPORT_ALREADY_RESOLVED);
        }
        report.resolve(processedAt);
    }

    @Transactional
    public void saveRecentCollection(final Long userId, final Long collectionId) {
        getCollectionById(collectionId);
        recentViewedCollectionRepository.upsertRecentViewedCollection(
            TSID.Factory.getTsid().toLong(),
            userId,
            collectionId,
            LocalDateTime.now()
        );
    }

    @Transactional
    public void deleteCollectionByUser(final Long userId){
        List<Collection> collectionList = collectionRepository.findAllByUserId(userId);
        collectionList.forEach(collectionContentRepository::deleteAllByCollection);
        recentViewedCollectionRepository.deleteAllByUserId(userId);
        collectionRepository.deleteAllByUserId(userId);
    }
}
