package kr.flint.api.admin.domain.system.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.api.admin.domain.system.controller.spec.AppVersionAdminControllerDocs;
import kr.flint.api.admin.domain.system.dto.request.AppVersionUpsertReq;
import kr.flint.api.admin.domain.system.dto.response.AdminAppVersionRes;
import kr.flint.api.admin.domain.system.service.AdminAppVersionFacade;
import kr.flint.api.admin.global.security.annotation.CurrentAdmin;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.system.domain.AppPlatform;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/app-versions")
public class AppVersionAdminController implements AppVersionAdminControllerDocs {

    private final AdminAppVersionFacade adminAppVersionFacade;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<List<AdminAppVersionRes>>> getAppVersions(
            @CurrentAdmin Long adminId
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
                SuccessCode.SUCCESS_FETCH,
                adminAppVersionFacade.getAppVersions()
        ));
    }

    @Override
    @PutMapping("/{platform}")
    public ResponseEntity<SuccessResponse<AdminAppVersionRes>> upsertAppVersion(
            @CurrentAdmin Long adminId,
            @PathVariable AppPlatform platform,
            @Valid @RequestBody AppVersionUpsertReq request
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
                SuccessCode.SUCCESS_UPDATE,
                adminAppVersionFacade.upsertAppVersion(platform, request)
        ));
    }
}
