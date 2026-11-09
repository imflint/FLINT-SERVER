package kr.flint.api.domain.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.system.domain.AppVersion;

@Schema(description = "강제 업데이트용 앱 버전 정책 응답")
public record AppVersionRes(
    @Schema(description = "최소 지원 버전 (x.y.z). 앱 버전이 이보다 낮으면 강제 업데이트 대상", example = "1.2.3")
    String version,

    @Schema(description = "스토어 이동 주소", example = "https://apps.apple.com/kr/app/id000000000")
    String path
) {
    public static AppVersionRes from(AppVersion appVersion) {
        return new AppVersionRes(appVersion.getMinSupportedVersion(), appVersion.getStorePath());
    }
}
