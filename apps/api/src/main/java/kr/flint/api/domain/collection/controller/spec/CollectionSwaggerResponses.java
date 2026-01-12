package kr.flint.api.domain.collection.controller.spec;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailListRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionSimpleRes;
import kr.flint.shared.dto.PaginationResponse;

@Schema(description = "Collection API 성공 응답 (Swagger 표시용)")
public interface CollectionSwaggerResponses {

	@Schema(name = "SuccessResponse_Void")
	record SuccessResponseVoid(
		@Schema(example = "SUCCESS") String code,
		@Schema(example = "요청이 성공했습니다.") String message,
		@Schema(nullable = true) Object data
	) {}

	@Schema(name = "SuccessResponse_GetCollectionDetailRes")
	record SuccessResponseGetCollectionDetailRes(
		@Schema(example = "SUCCESS_FETCH") String code,
		@Schema(example = "요청이 성공했습니다.") String message,
		@Schema(implementation = GetCollectionDetailRes.class)
		GetCollectionDetailRes data
	) {}

	@Schema(name = "SuccessResponse_Pagination_GetCollectionSimpleRes")
	record SuccessResponsePaginationGetCollectionSimpleRes(
		@Schema(example = "SUCCESS_FETCH") String code,
		@Schema(example = "요청이 성공했습니다.") String message,
		@Schema(implementation = PaginationResponseGetCollectionSimpleRes.class)
		PaginationResponseGetCollectionSimpleRes data
	) {}

	@Schema(name = "SuccessResponse_List_GetCollectionDetailListRes")
	record SuccessResponseListGetCollectionDetailListRes(
		@Schema(example = "SUCCESS_FETCH") String code,
		@Schema(example = "요청이 성공했습니다.") String message,
		@ArraySchema(schema = @Schema(implementation = GetCollectionDetailListRes.class))
		List<GetCollectionDetailListRes> data
	) {}

	@Schema(name = "PaginationResponse_GetCollectionSimpleRes")
	record PaginationResponseGetCollectionSimpleRes(
		@ArraySchema(schema = @Schema(implementation = GetCollectionSimpleRes.class))
		List<GetCollectionSimpleRes> data,

		@Schema(implementation = PaginationMeta.class)
		PaginationMeta meta
	) {}

	@Schema(name = "PaginationMeta")
	record PaginationMeta(
		@Schema(example = "OFFSET") String type,
		@Schema(example = "10") int returned,
		@Schema(example = "1") int currentPage,
		@Schema(example = "10") int totalPages,
		@Schema(example = "100") long totalElements,
		@Schema(example = "string", nullable = true) String nextCursor
	) {}
}
