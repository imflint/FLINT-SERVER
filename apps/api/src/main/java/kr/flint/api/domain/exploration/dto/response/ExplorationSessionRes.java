package kr.flint.api.domain.exploration.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탐색 세션 응답")
public record ExplorationSessionRes(
	@Schema(description = "현재 세션 전체 작품 목록. 숨김·삭제된 항목은 제외되어 30개보다 적을 수 있음", requiredMode = Schema.RequiredMode.REQUIRED)
	List<ExploreContentRes> items,
	@Schema(description = "세션 상태. PATCH 완료 시 END이며 다음 GET에서 새 세션 생성 시 IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
	ExplorationState state,
	@Schema(description = "현재 세션 이후 다음 30개가 준비되었는지 여부", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
	boolean hasNext,
	@Schema(description = "마지막으로 확인한 세션 내 위치. 새 세션 시작 전은 0", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
	int lastViewedPosition
) {
	public static ExplorationSessionRes of(List<ExploreContentRes> items, ExplorationState state, boolean hasNext) {
		return new ExplorationSessionRes(items, state, hasNext, 0);
	}

	public static ExplorationSessionRes of(
		List<ExploreContentRes> items,
		ExplorationState state,
		boolean hasNext,
		int lastViewedPosition
	) {
		return new ExplorationSessionRes(items, state, hasNext, lastViewedPosition);
	}

	// 아직 완전한 세션(30개)이 없을 때
	public static ExplorationSessionRes empty() {
		return new ExplorationSessionRes(List.of(), ExplorationState.EMPTY, false, 0);
	}
}
