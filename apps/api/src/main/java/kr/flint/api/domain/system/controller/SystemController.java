package kr.flint.api.domain.system.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.system.controller.spec.SystemControllerDocs;
import kr.flint.api.domain.system.dto.response.AppVersionRes;
import kr.flint.api.domain.system.service.SystemQueryFacade;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.system.domain.AppPlatform;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/system")
public class SystemController implements SystemControllerDocs {

    private final SystemQueryFacade systemQueryFacade;

    @Override
    @GetMapping("/version")
    public ResponseEntity<SuccessResponse<AppVersionRes>> getAppVersion(
            @RequestParam AppPlatform platform
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
                SuccessCode.SUCCESS_FETCH,
                systemQueryFacade.getAppVersion(platform)
        ));
    }
}
