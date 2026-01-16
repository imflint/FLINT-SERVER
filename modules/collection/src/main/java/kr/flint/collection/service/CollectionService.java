package kr.flint.collection.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContent;
import kr.flint.collection.domain.RecentViewedCollection;
import kr.flint.collection.dto.CollectionCreateCommand;
import kr.flint.collection.event.CollectionContentAddedEvent;
import kr.flint.collection.event.CollectionContentRemovedEvent;
import kr.flint.collection.exception.CollectionErrorCode;
import kr.flint.collection.exception.CollectionException;
import kr.flint.collection.repository.CollectionContentRepository;
import kr.flint.collection.repository.CollectionRepository;
import kr.flint.collection.repository.RecentViewedCollectionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {
	private final CollectionRepository collectionRepository;
	private final CollectionContentRepository collectionContentRepository;
	private final RecentViewedCollectionRepository recentViewedCollectionRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public void createCollection(final Long userId, final CollectionCreateCommand command, final String contentUrl) {
		Collection newCollection = Collection.create(
			command.title(),
			command.description(),
			contentUrl,
			command.isPublic(),
			userId
		);

		Collection savedCollection = collectionRepository.save(newCollection);

		List<CollectionContent> collectionContentList = command.contents().stream()
			.map(content -> CollectionContent.create(
				savedCollection,
				content.contentId(),
				content.isSpoiler(),
				content.reason()
			))
			.toList();

		collectionContentRepository.saveAll(collectionContentList);

		// 콘텐츠 추가 이벤트 발행 (CollectionKeyword 동기화 트리거)
		collectionContentList.forEach(content ->
			eventPublisher.publishEvent(new CollectionContentAddedEvent(savedCollection.getId(), content.getContentId()))
		);
	}

	public Collection getCollectionById(final Long collectionId) {
		return collectionRepository.findById(collectionId)
			.orElseThrow(() -> new CollectionException(CollectionErrorCode.COLLECTION_NOT_FOUND));
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
	public void saveRecentCollection(final Long userId, final Long collectionId) {
		Collection collection = getCollectionById(collectionId);
		recentViewedCollectionRepository
			.findByUserIdAndCollection(userId, collection)
			.ifPresentOrElse(
				RecentViewedCollection::updateViewedAt,
				() -> recentViewedCollectionRepository.save(
					RecentViewedCollection.create(userId, collection)
				)
			);
	}
}
