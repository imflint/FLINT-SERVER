package kr.flint.api.domain.exploration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "탐색 세션 상태")
public enum ExplorationState {
	@Schema(description = "탐색 진행 중 (items 30개)")
	IN_PROGRESS,
	@Schema(description = "현재 세션을 끝까지 봤고 다음 세트가 아직 없음(End). items에는 현재 세션 30개가 그대로 담김")
	END,
	@Schema(description = "아직 완전한 세션(30개)이 한 번도 준비되지 않음")
	EMPTY
}
