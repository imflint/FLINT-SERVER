package kr.flint.api.domain.exploration.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탐색 세션 응답")
public record ExplorationSessionRes(
	@Schema(description = "현재 세션 작품 목록 (IN_PROGRESS/END이면 30개, EMPTY이면 빈 배열)")
	List<ExploreContentRes> items,
	@Schema(description = "세션 상태")
	ExplorationState state,
	@Schema(description = "다음 세션(30개)이 준비되었는지 여부. END이면서 true이면 '다음 라운드 이용 가능'", example = "false")
	boolean hasNext
) {
	public static ExplorationSessionRes of(List<ExploreContentRes> items, ExplorationState state, boolean hasNext) {
		return new ExplorationSessionRes(items, state, hasNext);
	}

	// 아직 완전한 세션(30개)이 없을 때
	public static ExplorationSessionRes empty() {
		return new ExplorationSessionRes(List.of(), ExplorationState.EMPTY, false);
	}
}
