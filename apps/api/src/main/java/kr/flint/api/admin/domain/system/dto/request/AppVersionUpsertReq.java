package kr.flint.api.admin.domain.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "앱 최소 지원 버전 등록/변경 요청")
public record AppVersionUpsertReq(
    @Schema(description = "최소 지원 버전 (x.y.z)", example = "1.2.3")
    @NotBlank(message = "최소 지원 버전은 필수입니다.")
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "최소 지원 버전은 x.y.z 형식이어야 합니다.")
    String version,

    @Schema(description = "스토어 이동 주소", example = "https://apps.apple.com/kr/app/id000000000")
    @NotBlank(message = "스토어 주소는 필수입니다.")
    String path
) {
}
