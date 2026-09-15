package kr.flint.api.domain.exploration.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탐색 세션 응답")
public record ExplorationSessionRes(
	@Schema(description = "현재 세션 작품 목록. 숨김·삭제된 스냅샷 항목은 제외되어 30개보다 적을 수 있음")
	List<ExploreContentRes> items,
	@Schema(description = "세션 상태")
	ExplorationState state,
	@Schema(description = "다음 세션(30개)이 준비되었는지 여부. END이면서 true이면 '다음 라운드 이용 가능'", example = "false")
	boolean hasNext,
	@Schema(description = "마지막으로 확인한 세션 내 위치. 시작 전은 0", example = "12")
	int lastViewedPosition,
	@Schema(description = "현재 세션을 모두 소비해 다음 세션으로 이동할 수 있는지", example = "false")
	boolean canAdvance
) {
	public static ExplorationSessionRes of(List<ExploreContentRes> items, ExplorationState state, boolean hasNext) {
		return new ExplorationSessionRes(items, state, hasNext, 0, state == ExplorationState.END);
	}

	public static ExplorationSessionRes of(
		List<ExploreContentRes> items,
		ExplorationState state,
		boolean hasNext,
		int lastViewedPosition,
		boolean canAdvance
	) {
		return new ExplorationSessionRes(items, state, hasNext, lastViewedPosition, canAdvance);
	}

	// 아직 완전한 세션(30개)이 없을 때
	public static ExplorationSessionRes empty() {
		return new ExplorationSessionRes(List.of(), ExplorationState.EMPTY, false, 0, false);
	}
}
