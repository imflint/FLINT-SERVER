package kr.flint.collection.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContent;
import kr.flint.collection.dto.request.CreateCollectionReq;
import kr.flint.collection.repository.CollectionContentRepository;
import kr.flint.collection.repository.CollectionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {
	private final CollectionRepository collectionRepository;
	private final CollectionContentRepository collectionContentRepository;

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
}
