package kr.flint.api.admin.domain.system.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.system.domain.AppPlatform;
import kr.flint.system.domain.AppVersion;

@Schema(description = "앱 버전 정책 (어드민)")
public record AdminAppVersionRes(
    @Schema(description = "앱 플랫폼") AppPlatform platform,
    @Schema(description = "최소 지원 버전", example = "1.2.3") String version,
    @Schema(description = "스토어 이동 주소") String path,
    @Schema(description = "마지막 변경 시각") LocalDateTime updatedAt
) {
    public static AdminAppVersionRes from(AppVersion appVersion) {
        return new AdminAppVersionRes(
            appVersion.getPlatform(),
            appVersion.getMinSupportedVersion(),
            appVersion.getStorePath(),
            appVersion.getUpdatedAt()
        );
    }
}
