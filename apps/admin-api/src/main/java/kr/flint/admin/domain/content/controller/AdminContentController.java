package kr.flint.admin.domain.content.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.admin.domain.content.controller.spec.AdminContentControllerDocs;
import kr.flint.admin.domain.content.dto.request.AdminContentUpdateReq;
import kr.flint.admin.domain.content.dto.response.AdminContentRes;
import kr.flint.admin.domain.content.service.AdminContentFacade;
import kr.flint.admin.global.security.annotation.CurrentAdmin;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/contents")
public class AdminContentController implements AdminContentControllerDocs {

    private final AdminContentFacade adminContentFacade;

    @Override
    @PatchMapping("/{contentId}")
    public ResponseEntity<SuccessResponse<AdminContentRes>> updateContent(
        @CurrentAdmin Long adminId,
        @PathVariable Long contentId,
        @Valid @RequestBody AdminContentUpdateReq request
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_UPDATE,
            adminContentFacade.updateContent(adminId, contentId, request)
        ));
    }
}
