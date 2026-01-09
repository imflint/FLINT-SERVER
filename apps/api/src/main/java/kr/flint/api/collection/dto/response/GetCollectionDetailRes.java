package kr.flint.api.collection.dto.response;

import kr.flint.collection.dto.response.GetCollectionSimpleRes;

public record GetCollectionDetailRes(
	GetCollectionSimpleRes getCollectionSimpleRes
) {
}
