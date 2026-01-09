package kr.flint.collection.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContent;
import kr.flint.collection.domain.RecentViewedCollection;
import kr.flint.collection.dto.request.CreateCollectionReq;
import kr.flint.collection.dto.response.GetCollectionSimpleRes;
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

	@Transactional
	public void createCollection(final Long userId, final CreateCollectionReq createCollectionReq) {
		//Collection 생성
		Collection newCollection = Collection.create(
			createCollectionReq.title(),
			createCollectionReq.description(),
			createCollectionReq.imageUrl(),
			createCollectionReq.isPublic(),
			userId
		);

		//Collection 저장
		Collection savedCollection = collectionRepository.save(newCollection);

		//CollectionContent 중간매핑 엔티티 저장
		List<CollectionContent> collectionContentList = createCollectionReq.contentList().stream()
			.map(req -> CollectionContent.create(
				savedCollection,
				req.contentId(),
				req.isSpoiler(),
				req.reason()
			))
			.toList();

		collectionContentRepository.saveAll(collectionContentList);
	}

	public GetCollectionSimpleRes getCollectionSimple(final Long collectionId) {
		Collection collection = getCollectionById(collectionId);
		return GetCollectionSimpleRes.of(collection);
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
	}

	@Transactional
	public void saveRecentCollection(final Long userId, final Long collectionId) {
		Collection collection = getCollectionById(collectionId);
		RecentViewedCollection recentViewedCollection = RecentViewedCollection.create(userId, collection);
		recentViewedCollectionRepository.save(recentViewedCollection);
	}
}
