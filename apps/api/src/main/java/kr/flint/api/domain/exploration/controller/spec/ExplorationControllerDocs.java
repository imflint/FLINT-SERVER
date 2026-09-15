package kr.flint.api.domain.exploration.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.dto.request.UpdateExplorationProgressReq;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import kr.flint.shared.dto.response.SuccessResponse;

@Tag(name = "Exploration", description = "탐색 API")
public interface ExplorationControllerDocs {

	@Operation(
		summary = "탐색 세션 조회",
		description = """
			현재 로그인한 사용자의 탐색 세션(작품 30개)을 조회합니다. 진행 상태는 서버가 관리합니다.

			- 한 세션은 작품 30개를 스냅샷으로 고정하며, 세션 내 작품은 중복되지 않습니다.
			- 스냅샷 생성 뒤 비공개·삭제된 항목은 제외하고 다른 작품으로 충원하지 않으므로 응답 항목은 30개보다 적을 수 있습니다.
			- 응답 `state`:
			  - `IN_PROGRESS` → 탐색 진행 중
			  - `END` → 현재 세션을 끝까지 본 상태. 앱을 종료·재진입해도 진행 위치와 현재 세션을 유지합니다.
			  - `EMPTY` → 아직 완전한 세션(30개)이 준비되지 않음 (items 빈 배열)
			- `lastViewedPosition`은 마지막으로 확인한 스냅샷 위치이고 `canAdvance=true`일 때만 `POST /exploration/next`를 호출할 수 있습니다.
			- `hasNext`는 다음 세션(30개)이 준비되었는지를 나타냅니다.
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
		@ApiResponse(responseCode = "200", description = "처리 성공", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "409", description = "현재 세션을 아직 모두 확인하지 않음")
	})
	ResponseEntity<SuccessResponse<ExplorationSessionRes>> advanceExplorationSession(
		@Parameter(hidden = true) Long userId
	);

	@Operation(
		summary = "탐색 진행 위치 저장",
		description = "마지막으로 확인한 작품 위치를 저장합니다. 같은 위치 재전송은 멱등 처리하며, 위치 회귀와 건너뛰기는 409로 거부합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "저장 성공", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "409", description = "허용되지 않은 위치 이동")
	})
	ResponseEntity<SuccessResponse<ExplorationSessionRes>> updateExplorationProgress(
		@Parameter(hidden = true) Long userId,
		@RequestBody(required = true) UpdateExplorationProgressReq request
	);
}
