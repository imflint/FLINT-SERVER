package kr.flint.api.domain.collection.service;


import java.util.List;

import org.springframework.stereotype.Component;

import kr.flint.api.domain.collection.dto.response.GetCollectionDetailRes;
import kr.flint.api.domain.collection.repository.CollectionQueryRepository;
import kr.flint.api.domain.collection.dto.response.GetCollectionSimpleRes;
import kr.flint.collection.exception.CollectionErrorCode;
import kr.flint.collection.exception.CollectionException;
import kr.flint.collection.service.CollectionService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.SliceCursor;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CollectionQueryFacade {
	private final CollectionQueryService collectionQueryService;
	private final CollectionQueryRepository collectionQueryRepository;
	private final CollectionService collectionService;
	private final CloudFrontUrlProvider cloudFrontUrlProvider;

	public PaginationResponse<GetCollectionSimpleRes> getCollectionList(Long cursor, int size){
		SliceCursor<GetCollectionSimpleRes> sliceCursor = collectionQueryService.getCollectionList(cursor, size);
		return PaginationResponse.ofCursor(sliceCursor);
	}

	public GetCollectionDetailRes getCollectionDetail(final Long collectionId, final Long userId){
		CollectionQueryRepository.GetCollectionHeader header = collectionQueryRepository.getHeader(collectionId, userId);
		List<GetCollectionDetailRes.Content> contentList = collectionQueryRepository.getContentList(collectionId, userId)
			.stream()
			.map(content -> content.resolveImages(cloudFrontUrlProvider::resolveUrl))
			.toList();

		if(header == null){
			throw new CollectionException(CollectionErrorCode.COLLECTION_NOT_FOUND);
		}

		return new GetCollectionDetailRes(
			header.collectionId(),
			header.title(),
			header.description(),
			cloudFrontUrlProvider.resolveUrl(header.imageUrl()),
			header.createdAt().toLocalDate(),
			header.isBookmarked(),
			header.isPublic(),
			header.toAuthor(),
			contentList
		);
	}
}
