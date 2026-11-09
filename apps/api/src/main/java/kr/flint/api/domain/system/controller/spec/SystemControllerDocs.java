package kr.flint.api.domain.system.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.domain.system.dto.response.AppVersionRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.system.domain.AppPlatform;

@Tag(name = "System", description = "앱 운영 공통 API")
public interface SystemControllerDocs {

    @Operation(summary = "최소 지원 버전 조회 (강제 업데이트)",
               description = """
                   플랫폼별 최소 지원 버전과 스토어 주소를 반환합니다. **인증이 필요하지 않습니다.**

                   - 앱은 진입 시점과 백그라운드 복귀 시점에 호출합니다.
                   - 앱 버전을 `x.y.z` 세 자리로 비교해 `version` 보다 낮으면
                     닫을 수 없는 팝업을 띄우고, 버튼은 `path` 로 이동시킵니다.
                   - **조회가 실패하면 앱은 그냥 통과시켜야 합니다.** 서버 장애로 앱 전체가
                     막히는 것을 막기 위한 계약입니다. 정책 미등록 시 `404` 가 내려가는 것도
                     같은 이유로 통과 대상입니다.
                   - 최소 버전은 어드민에서 변경하며, 앱 재배포 없이 즉시 적용됩니다.
                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "해당 플랫폼의 버전 정책 미등록 (앱은 통과 처리)")
    })
    ResponseEntity<SuccessResponse<AppVersionRes>> getAppVersion(
            @Parameter(description = "앱 플랫폼", required = true, example = "IOS") AppPlatform platform
    );
}
