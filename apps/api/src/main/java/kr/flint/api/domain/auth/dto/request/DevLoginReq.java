package kr.flint.api.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "개발용 로그인 요청 (dev/local 환경에서만 사용)")
public record DevLoginReq(
        @Schema(description = "사용자 ID", example = "1")
        @NotNull(message = "userId는 필수입니다")
        Long userId
) {
}
