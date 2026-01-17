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

		// S3 key를 CloudFront URL로 변환
		List<GetCollectionSimpleRes> resolvedList = collectionList.stream()
			.map(c -> c.withResolvedImageUrl(cloudFrontUrlProvider::resolveUrl))
			.toList();

		String nextCursor = hasNext
			? String.valueOf(collectionList.get(collectionList.size() - 1).collectionId())
			: null;

		String currentCursor = cursor != null ? String.valueOf(cursor) : null;

		return SliceCursor.of(resolvedList, currentCursor, nextCursor);
	}

	public List<GetCollectionDetailListRes> getRecentCollectionList(final Long userId){
		List<GetCollectionDetailListRes> collectionList = collectionQueryRepository.getCollectionDetailList(userId);
		return collectionList.isEmpty() ? null : collectionList;
	}
}
