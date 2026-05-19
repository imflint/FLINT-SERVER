package kr.flint.admin.domain.account.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.admin.domain.account.controller.spec.AdminAccountControllerDocs;
import kr.flint.admin.domain.account.dto.request.AdminAccountUpdateReq;
import kr.flint.admin.domain.account.dto.response.AdminMeRes;
import kr.flint.admin.domain.account.service.AdminAccountFacade;
import kr.flint.admin.global.security.annotation.CurrentAdmin;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/me")
public class AdminAccountController implements AdminAccountControllerDocs {

    private final AdminAccountFacade adminAccountFacade;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<AdminMeRes>> getMe(@CurrentAdmin Long adminId) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminAccountFacade.getMe(adminId)
        ));
    }

    @Override
    @PatchMapping
    public ResponseEntity<SuccessResponse<AdminMeRes>> updateMe(
        @CurrentAdmin Long adminId,
        @Valid @RequestBody AdminAccountUpdateReq request
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_UPDATE,
            adminAccountFacade.updateMe(adminId, request)
        ));
    }
}
