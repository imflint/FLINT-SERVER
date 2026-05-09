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
import kr.flint.api.domain.collection.dto.request.CreateCollectionReq;
import kr.flint.api.domain.collection.dto.request.ReportCollectionReq;
import kr.flint.api.domain.collection.dto.request.UpdateCollectionReq;
import kr.flint.api.domain.collection.dto.response.CreateCollectionRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailListRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionListRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionSimpleRes;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Collection", description = "컬렉션 API")
public interface CollectionControllerDocs {

	@Operation(
		summary = "컬렉션 생성 - 재민",
		description = "새로운 컬렉션을 생성합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "생성 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 요청 (유효성 검증 실패)",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<CreateCollectionRes>> postCollection(
		Long userId,
		@Parameter(description = "컬렉션 생성 요청 바디")
		CreateCollectionReq createCollectionReq
	);

	@Operation(
		summary = "컬렉션 수정 - 재민",
		description = """
			기존 컬렉션의 메타데이터(제목/설명/대표 이미지/공개여부)와 작품 리스트를 통째로 교체합니다.

			- 작성자(userId == collection.userId)만 호출 가능 — 그 외 요청은 403.
			- 작품 리스트는 **전체 교체** 전략 (Diff가 아닌 완전 대체). 추가된 작품에는 ContentAdded, 빠진 작품에는 ContentRemoved 이벤트가 발행됩니다.
			- `imageUrl` 미지정 시 첫 작품의 TMDB 포스터를 대표 이미지로 사용합니다 (생성과 동일 규칙).
			"""
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "수정 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 요청 (유효성 검증 실패)",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		),
		@ApiResponse(
			responseCode = "403",
			description = "수정 권한 없음 (작성자가 아님)",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		),
		@ApiResponse(
			responseCode = "404",
			description = "존재하지 않는 컬렉션",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<Void>> updateCollection(
		Long userId,
		@Parameter(description = "수정할 컬렉션 ID", example = "800388257884431200")
		Long collectionId,
		@Parameter(description = "컬렉션 수정 요청 바디")
		UpdateCollectionReq updateCollectionReq
	);

	@Operation(
		summary = "컬렉션 신고 - 재민",
		description = """
			부적절한 컬렉션을 신고합니다. 접수 시 운영자 Discord 채널로 즉시 알림이 전송됩니다.

			**신고 사유 (복수 선택 가능, 1개 이상 필수)**
            - `ABUSE` — 욕설·혐오 표현이 포함된 콘텐츠
            - `OBSCENE` — 음란하거나 선정적인 콘텐츠
            - `SPAM` — 광고·홍보 또는 스팸성 콘텐츠
            - `COPYRIGHT` — 저작권을 침해한 콘텐츠
            - `OTHER` — 기타 (이 경우 `otherDetail` 에 0~200자 자유 입력)

			Discord 알림 전송은 신고 트랜잭션 commit 이후 비동기로 실행되며, 알림 실패가 신고 접수 자체를 무효화하지 않습니다.

			**요청 예시**
			```json
			{
			  "reasons": ["SPAM", "OTHER"],
			  "otherDetail": "외부 결제 사이트로 유도하는 링크가 포함되어 있어요"
			}
			```
			"""
	)
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "신고 접수 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 요청 (사유 미선택, otherDetail 200자 초과 등)",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		),
		@ApiResponse(
			responseCode = "404",
			description = "존재하지 않는 컬렉션",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<Void>> reportCollection(
		Long userId,
		@Parameter(description = "신고할 컬렉션 ID", example = "800388257884431200")
		Long collectionId,
		@Parameter(description = "컬렉션 신고 요청 바디")
		ReportCollectionReq reportCollectionReq
	);

	@Operation(
		summary = "컬렉션 목록 조회 (커서 페이지네이션) - 재민",
		description = "cursor와 size를 기반으로 컬렉션 목록을 조회합니다. cursor가 없으면 첫 페이지를 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 요청",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<PaginationResponse<GetCollectionSimpleRes>>> discoverCollectionList(
		@Parameter(description = "커서(마지막 아이템 기준)", example = "", required = false)
		Long cursor,
		@Parameter(description = "페이지 크기", example = "10")
		int size
	);

	@Operation(
		summary = "컬렉션 상세 조회 - 재민",
		description = "collectionId로 컬렉션 상세 정보를 조회합니다. (조회 시 최근 본 컬렉션 저장 로직이 실행될 수 있습니다.)"
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
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
		summary = "최근 본 컬렉션 목록 조회 - 재민",
		description = "사용자가 최근에 조회한 컬렉션 목록을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<SuccessResponse<GetCollectionListRes>> getRecentCollectionList(Long userId);
}
