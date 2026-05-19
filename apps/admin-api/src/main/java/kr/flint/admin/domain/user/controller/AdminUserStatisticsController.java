package kr.flint.admin.domain.user.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.admin.domain.user.controller.spec.AdminUserStatisticsControllerDocs;
import kr.flint.admin.domain.user.dto.request.AdminDailyUserMetricsRange;
import kr.flint.admin.domain.user.dto.response.AdminDailyUserMetricsRes;
import kr.flint.admin.domain.user.dto.response.AdminUserStatisticsRes;
import kr.flint.admin.domain.user.service.AdminUserStatisticsFacade;
import kr.flint.admin.global.security.annotation.CurrentAdmin;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserStatisticsController implements AdminUserStatisticsControllerDocs {

    private final AdminUserStatisticsFacade adminUserStatisticsFacade;

    @Override
    @GetMapping("/statistics")
    public ResponseEntity<SuccessResponse<AdminUserStatisticsRes>> getStatistics(@CurrentAdmin Long adminId) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminUserStatisticsFacade.getStatistics(adminId)
        ));
    }

    @Override
    @GetMapping("/daily-activity")
    public ResponseEntity<SuccessResponse<AdminDailyUserMetricsRes>> getDailyActivity(
        @CurrentAdmin Long adminId,
        @RequestParam(required = false, defaultValue = "DAYS_30") AdminDailyUserMetricsRange range,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminUserStatisticsFacade.getDailyActivity(adminId, range, from, to)
        ));
    }
}
