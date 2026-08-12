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
			현재 로그인한 사용자의 탐색 세션(작품 30개)을 조회합니다. 진행 상태는 서버가 관리합니다.

			- 한 세션은 작품 30개로 고정이며, 세션 내 작품은 중복되지 않습니다.
			- 응답 `state`:
			  - `IN_PROGRESS` → 탐색 진행 중 (items 30개)
			  - `END` → 현재 세션을 끝까지 봤고 다음 세트가 아직 없음. **items에 현재 세션 30개가 그대로 담겨 있어 위로 스크롤해 재열람 가능.** 앱을 종료·재진입해도 서버가 상태를 기억하므로 다시 `END`가 반환됩니다.
			  - `EMPTY` → 아직 완전한 세션(30개)이 준비되지 않음 (items 빈 배열)
			- `hasNext` → 다음 세션(30개)이 준비되었는지. `END`이면서 `true`이면 '다음 라운드 이용 가능' → `POST /exploration/next`로 넘어갈 수 있습니다.
			"""
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<SuccessResponse<ExplorationSessionRes>> getExplorationSession(
		@Parameter(hidden = true) Long userId
	);

	@Operation(
		summary = "다음 탐색 세션으로 이동",
		description = """
			현재 세션을 끝까지 본 사용자를 다음 세션으로 넘깁니다. (사용자가 현재 세션의 마지막 작품에 도달했을 때 호출)

			- 다음 세트(30개)가 준비돼 있으면 다음 세션으로 **전진**하여 반환합니다. (`state=IN_PROGRESS`)
			- 다음 세트가 아직 없으면 **End로 기록**하고 현재 세션을 그대로 반환합니다. (`state=END`) — 이후 재진입 시에도 End가 유지됩니다.
			"""
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "처리 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<SuccessResponse<ExplorationSessionRes>> advanceExplorationSession(
		@Parameter(hidden = true) Long userId
	);
}
