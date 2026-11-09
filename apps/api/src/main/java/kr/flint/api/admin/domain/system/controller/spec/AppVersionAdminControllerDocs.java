package kr.flint.api.admin.domain.system.controller.spec;

import java.util.List;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.flint.api.admin.domain.system.dto.request.AppVersionUpsertReq;
import kr.flint.api.admin.domain.system.dto.response.AdminAppVersionRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.system.domain.AppPlatform;

@Tag(name = "Admin - App Version", description = "강제 업데이트 정책 관리 API")
public interface AppVersionAdminControllerDocs {

    @Operation(summary = "앱 버전 정책 목록 조회",
               description = "플랫폼별로 등록된 최소 지원 버전과 스토어 주소를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<List<AdminAppVersionRes>>> getAppVersions(
            @Parameter(hidden = true) Long adminId
    );

    @Operation(summary = "앱 최소 지원 버전 등록/변경",
               description = """
                   플랫폼의 최소 지원 버전과 스토어 주소를 등록하거나 변경합니다. **앱 재배포 없이 즉시 적용됩니다.**

                   - 해당 플랫폼 정책이 없으면 생성하고, 있으면 덮어씁니다(멱등).
                   - `version` 은 `x.y.z` 형식만 허용합니다. 형식이 깨진 값이 저장되면
                     전체 사용자가 닫을 수 없는 팝업에 갇히기 때문에 서버에서 거부합니다.
                   - ⚠️ 이 값을 올리면 해당 버전 미만 사용자 전원이 즉시 강제 업데이트 대상이 됩니다.
                     스토어 심사가 통과되어 새 버전이 실제로 내려받을 수 있는 상태인지 먼저 확인하세요.
                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록/변경 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "버전 형식 오류 또는 스토어 주소 누락")
    })
    ResponseEntity<SuccessResponse<AdminAppVersionRes>> upsertAppVersion(
            @Parameter(hidden = true) Long adminId,
            @Parameter(description = "앱 플랫폼", required = true, example = "IOS") AppPlatform platform,
            @Valid AppVersionUpsertReq request
    );
}
