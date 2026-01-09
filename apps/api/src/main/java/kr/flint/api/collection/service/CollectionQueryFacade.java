package kr.flint.api.collection.service;


import org.springframework.stereotype.Component;

import kr.flint.collection.dto.response.GetCollectionSimpleRes;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.SliceCursor;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CollectionQueryFacade {
	private final CollectionQueryService collectionQueryService;

	public PaginationResponse<GetCollectionSimpleRes> getCollectionList(Long cursor, int size){
		SliceCursor<GetCollectionSimpleRes> sliceCursor = collectionQueryService.getCollectionList(cursor, size);
		return PaginationResponse.ofCursor(sliceCursor);
	}
}
