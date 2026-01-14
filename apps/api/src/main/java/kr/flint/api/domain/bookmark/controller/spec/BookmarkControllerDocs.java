package kr.flint.api.domain.bookmark.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.domain.bookmark.dto.response.GetBookmarkUserRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Bookmark", description = "북마크 API")
public interface BookmarkControllerDocs {

	@Operation(
		summary = "콘텐츠 북마크 토글",
		description = "콘텐츠를 북마크하거나 이미 북마크된 경우 해제합니다."
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "토글 성공",
			content = @Content(
				schema = @Schema(implementation = BookmarkToggleSwaggerResponse.class)
			)
		),
		@ApiResponse(
			responseCode = "404",
			description = "존재하지 않는 콘텐츠",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<Boolean>> postContentBookmark(
		Long userId,
		@Parameter(description = "콘텐츠 ID", example = "1")
		Long contentId
	);

	@Operation(
		summary = "컬렉션 북마크 토글",
		description = "컬렉션을 북마크하거나 이미 북마크된 경우 해제합니다."
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "토글 성공",
			content = @Content(
				schema = @Schema(implementation = BookmarkToggleSwaggerResponse.class)
			)
		),
		@ApiResponse(
			responseCode = "404",
			description = "존재하지 않는 컬렉션",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<Boolean>> postCollectionBookmark(
		Long userId,
		@Parameter(description = "컬렉션 ID", example = "1")
		Long collectionId
	);

	@Operation(
		summary = "컬렉션 북마크 유저 조회",
		description = "특정 컬렉션을 북마크한 유저 목록과 총 북마크 수를 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "조회 성공",
			content = @Content(
				schema = @Schema(implementation = GetBookmarkedUserSwaggerResponse.class)
			)
		),
		@ApiResponse(
			responseCode = "404",
			description = "존재하지 않는 컬렉션",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<GetBookmarkUserRes>> getBookmarkedUser(
		@Parameter(description = "컬렉션 ID", example = "1")
		Long collectionId
	);
}
