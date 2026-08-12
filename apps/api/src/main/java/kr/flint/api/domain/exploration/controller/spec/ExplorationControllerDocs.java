package kr.flint.api.domain.exploration.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.shared.dto.response.SuccessResponse;

@Tag(name = "Exploration", description = "탐색 API")
public interface ExplorationControllerDocs {

	@Operation(
		summary = "탐색 세션 조회",
		description = """
			탐색 페이지의 한 세션(작품 30개)을 조회합니다.

			- 한 세션은 작품 30개로 고정이며, 세션 내 작품은 중복되지 않습니다.
			- `cursor` 없이 호출하면 첫 세션을 조회합니다.
			- 세션의 30개를 모두 소진하면 응답의 `nextCursor`로 다음 세션을 요청합니다.
			- 다음 세션을 구성할 30개가 아직 확보되지 않았으면 `items`는 빈 배열, `nextCursor`는 `-1`(End)로 반환됩니다.
			- 진행 위치(몇 번째까지 봤는지)는 서버가 관리하지 않습니다. 클라이언트가 `cursor`만 보관합니다.
			  같은 `cursor`로 재요청하면 동일한 30개가 반환됩니다(중간에 앱을 종료·재진입해도 해당 세트가 유지됨).
			"""
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<SuccessResponse<ExplorationSessionRes>> getExplorationSession(
		@Parameter(description = "이전 응답의 nextCursor. 첫 세션은 미입력", example = "801473411402740986")
		Long cursor
	);
}
