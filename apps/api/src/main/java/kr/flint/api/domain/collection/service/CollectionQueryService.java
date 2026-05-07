package kr.flint.api.domain.collection.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.collection.dto.response.GetCollectionDetailListRes;
import kr.flint.api.domain.collection.repository.CollectionQueryRepository;
import kr.flint.api.domain.collection.dto.response.GetCollectionSimpleRes;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.shared.dto.SliceCursor;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionQueryService {
	private final CollectionQueryRepository collectionQueryRepository;
	private final CloudFrontUrlProvider cloudFrontUrlProvider;

	public SliceCursor<GetCollectionSimpleRes> getCollectionList(final Long cursor, final int size){
		List<GetCollectionSimpleRes> fetchedCollections = collectionQueryRepository.getCollectionSimpleList(cursor, size);

		boolean hasNext = fetchedCollections.size() > size;
		List<GetCollectionSimpleRes> collectionList = hasNext
			? fetchedCollections.subList(0, size)
			: fetchedCollections;
		List<GetCollectionSimpleRes> resolvedCollectionList = collectionList.stream()
			.map(collection -> new GetCollectionSimpleRes(
				collection.collectionId(),
				cloudFrontUrlProvider.resolveUrl(collection.imageUrl()),
				collection.contentTitle(),
				collection.contentDescription()
			))
			.toList();

		String nextCursor = hasNext
			? String.valueOf(collectionList.getLast().collectionId())
			: "";

		String currentCursor = cursor != null ? String.valueOf(cursor) : null;

		return SliceCursor.of(resolvedCollectionList, currentCursor, nextCursor);
	}

	public List<GetCollectionDetailListRes> getRecentCollectionList(final Long userId){
		List<GetCollectionDetailListRes> collectionList = collectionQueryRepository.getCollectionDetailList(userId);
		return collectionList.stream()
			.map(collection -> new GetCollectionDetailListRes(
				collection.id(),
				cloudFrontUrlProvider.resolveUrl(collection.thumbnailUrl()),
				collection.title(),
				collection.description(),
				collection.imageList().stream()
					.map(cloudFrontUrlProvider::resolveUrl)
					.toList(),
				collection.bookmarkCount(),
				collection.isBookmarked(),
				collection.userId(),
				collection.nickname(),
				cloudFrontUrlProvider.resolveUrl(collection.profileImageUrl())
			))
			.toList();
	}
}
