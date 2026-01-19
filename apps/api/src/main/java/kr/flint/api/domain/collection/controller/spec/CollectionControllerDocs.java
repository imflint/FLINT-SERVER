package kr.flint.api.domain.collection.controller.spec;

import java.util.List;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailListRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailRes;
import kr.flint.api.domain.collection.dto.request.CreateCollectionReq;
import kr.flint.api.domain.collection.dto.response.GetCollectionSimpleRes;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Collection", description = "컬렉션 API")
public interface CollectionControllerDocs {

	@Operation(
		summary = "컬렉션 생성",
		description = "새로운 컬렉션을 생성합니다."
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "생성 성공",
			content = @Content(schema = @Schema(implementation = CollectionSwaggerResponses.SuccessResponseVoid.class))
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 요청 (유효성 검증 실패)",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<?>> postCollection(
		Long userId,
		@Parameter(description = "컬렉션 생성 요청 바디")
		CreateCollectionReq createCollectionReq
	);

	@Operation(
		summary = "컬렉션 목록 조회 (커서 페이지네이션)",
		description = "cursor와 size를 기반으로 컬렉션 목록을 조회합니다. cursor가 없으면 첫 페이지를 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "조회 성공",
			content = @Content(schema = @Schema(implementation = CollectionSwaggerResponses.SuccessResponsePaginationGetCollectionSimpleRes.class))
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 요청",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<PaginationResponse<GetCollectionSimpleRes>>> discoverCollectionList(
		@Parameter(description = "커서(마지막 아이템 기준)", example = "1", required = false)
		Long cursor,
		@Parameter(description = "페이지 크기", example = "10")
		int size
	);

	@Operation(
		summary = "컬렉션 상세 조회",
		description = "collectionId로 컬렉션 상세 정보를 조회합니다. (조회 시 최근 본 컬렉션 저장 로직이 실행될 수 있습니다.)"
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "조회 성공",
			content = @Content(schema = @Schema(implementation = CollectionSwaggerResponses.SuccessResponseGetCollectionDetailRes.class))
		),
		@ApiResponse(
			responseCode = "404",
			description = "존재하지 않는 컬렉션",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<GetCollectionDetailRes>> getCollectionDetail(
		Long userId,
		@Parameter(description = "컬렉션 ID", example = "1")
		Long collectionId
	);

	@Operation(
		summary = "최근 본 컬렉션 목록 조회",
		description = "사용자가 최근에 조회한 컬렉션 목록을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "조회 성공",
			content = @Content(schema = @Schema(implementation = CollectionSwaggerResponses.SuccessResponseListGetCollectionDetailListRes.class))
		)
	})
	ResponseEntity<SuccessResponse<List<GetCollectionDetailListRes>>> getRecentCollectionList(Long userId);
}
