package kr.flint.api.domain.exploration.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탐색 세션 응답")
public record ExplorationSessionRes(
	@Schema(description = "탐색 세션 작품 목록 (정확히 30개, 다음 세션이 준비되지 않았으면 빈 배열)")
	List<ExploreContentRes> items,
	@Schema(
		description = "다음 세션 요청용 커서. 다음 세션(30개)이 아직 확보되지 않았으면 -1(End)",
		example = "801473411402740986"
	)
	long nextCursor
) {
	private static final long END_CURSOR = -1L;

	public static ExplorationSessionRes of(List<ExploreContentRes> items, long nextCursor) {
		return new ExplorationSessionRes(items, nextCursor);
	}

	// 다음 세션을 구성할 30개가 아직 없을 때 (End / 초기 빈 상태)
	public static ExplorationSessionRes end() {
		return new ExplorationSessionRes(List.of(), END_CURSOR);
	}
}
