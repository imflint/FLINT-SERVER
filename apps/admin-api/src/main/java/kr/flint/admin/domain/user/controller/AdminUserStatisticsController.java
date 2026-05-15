package kr.flint.admin.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.admin.domain.user.controller.spec.AdminUserStatisticsControllerDocs;
import kr.flint.admin.domain.user.dto.response.AdminUserStatisticsRes;
import kr.flint.admin.domain.user.service.AdminUserStatisticsFacade;
import kr.flint.admin.global.security.annotation.CurrentAdmin;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users/statistics")
public class AdminUserStatisticsController implements AdminUserStatisticsControllerDocs {

    private final AdminUserStatisticsFacade adminUserStatisticsFacade;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<AdminUserStatisticsRes>> getStatistics(@CurrentAdmin Long adminId) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminUserStatisticsFacade.getStatistics(adminId)
        ));
    }
}
