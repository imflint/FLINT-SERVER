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

			- 세션이 없으면 생성하고, 진행 중이면 같은 작품 30개 스냅샷을 재사용합니다.
			- 현재 세션을 모두 확인한 뒤 GET하면 다음 30개 세션을 자동 생성합니다. 한 요청에서는 최대 한 번만 전환합니다.
			- 다음 30개가 준비되지 않으면 현재 완료 세션과 진행 위치를 유지합니다.
			- 스냅샷 생성 뒤 비공개·삭제된 항목은 제외하고 다른 작품으로 충원하지 않으므로 응답 항목은 30개보다 적을 수 있습니다.
			- 응답 `state`:
			  - `IN_PROGRESS` → 탐색 진행 중
			  - `END` → 현재 세션을 모두 확인함. 다음 30개가 없으면 유지하며, 전환한 새 세션도 모두 숨김이면 다음 GET에서 다시 전환 시도
			  - `EMPTY` → 아직 완전한 세션(30개)이 준비되지 않음 (items 빈 배열)
			- `lastViewedPosition`은 마지막으로 확인한 스냅샷 위치입니다. 새 세션에서는 0부터 시작합니다.
			- `hasNext`는 다음 세션(30개)이 준비되었는지를 나타냅니다.
			- 응답 목록은 현재 세션 전체이므로 기존 목록에 추가하지 않고 교체합니다. 응답은 캐시하지 않습니다.
			"""
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<SuccessResponse<ExplorationSessionRes>> getExplorationSession(
		@Parameter(hidden = true) Long userId
	);

	@Operation(
		summary = "탐색 진행 위치 저장",
		description = """
			마지막으로 확인한 작품 위치를 저장하고 현재 세션 전체를 반환합니다.
			현재 세션의 같은 위치 재전송은 멱등 처리하며, 위치 회귀와 건너뛰기는 409로 거부합니다.
			마지막 작품 확인 시 state=END를 반환하고, 새 세션은 다음 GET에서 생성합니다.
			프론트는 GET과 PATCH를 순차 처리해야 합니다. 요청에 세션 버전이 없어 이전 세션의 지연 요청은 식별할 수 없습니다.
			"""
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
