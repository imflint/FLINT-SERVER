package kr.flint.admin.domain.user.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.admin.domain.user.controller.spec.AdminUserManagementControllerDocs;
import kr.flint.admin.domain.user.dto.request.AdminUserModerationReq;
import kr.flint.admin.domain.user.dto.response.AdminUserDetailRes;
import kr.flint.admin.domain.user.dto.response.AdminUserSummaryRes;
import kr.flint.admin.domain.user.service.AdminUserManagementFacade;
import kr.flint.admin.global.security.annotation.CurrentAdmin;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.user.domain.UserStatus;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserManagementController implements AdminUserManagementControllerDocs {

    private final AdminUserManagementFacade adminUserManagementFacade;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<PaginationResponse<AdminUserSummaryRes>>> getUsers(
        @CurrentAdmin Long adminId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) UserStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminUserManagementFacade.getUsers(adminId, keyword, status, createdFrom, createdTo, page, size)
        ));
    }

    @Override
    @GetMapping("/{userId}")
    public ResponseEntity<SuccessResponse<AdminUserDetailRes>> getUser(
        @CurrentAdmin Long adminId,
        @PathVariable Long userId
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminUserManagementFacade.getUser(adminId, userId)
        ));
    }

    @Override
    @PostMapping("/{userId}/moderations")
    public ResponseEntity<SuccessResponse<AdminUserDetailRes>> moderateUser(
        @CurrentAdmin Long adminId,
        @PathVariable Long userId,
        @Valid @RequestBody AdminUserModerationReq request
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_UPDATE,
            adminUserManagementFacade.moderateUser(adminId, userId, request)
        ));
    }
}
