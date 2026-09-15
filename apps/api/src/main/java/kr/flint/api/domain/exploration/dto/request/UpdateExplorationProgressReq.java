package kr.flint.api.domain.exploration.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateExplorationProgressReq(
	@Schema(description = "마지막으로 확인한 세션 내 위치", example = "12")
	@Min(value = 1, message = "lastViewedPosition은 1 이상이어야 합니다.")
	@Max(value = 30, message = "lastViewedPosition은 30 이하여야 합니다.")
	int lastViewedPosition
) {}
