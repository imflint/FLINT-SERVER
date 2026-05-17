package kr.flint.admin.domain.report.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.admin.domain.report.controller.spec.AdminCollectionReportControllerDocs;
import kr.flint.admin.domain.report.dto.request.AdminCollectionReportResolutionReq;
import kr.flint.admin.domain.report.dto.response.AdminCollectionReportDetailRes;
import kr.flint.admin.domain.report.dto.response.AdminCollectionReportSummaryRes;
import kr.flint.admin.domain.report.service.AdminCollectionReportFacade;
import kr.flint.admin.global.security.annotation.CurrentAdmin;
import kr.flint.collection.domain.ReportStatus;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/reports/collections")
public class AdminCollectionReportController implements AdminCollectionReportControllerDocs {

    private final AdminCollectionReportFacade adminCollectionReportFacade;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<PaginationResponse<AdminCollectionReportSummaryRes>>> getReports(
        @CurrentAdmin Long adminId,
        @RequestParam(required = false) ReportStatus status,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminCollectionReportFacade.getReports(adminId, status, page, size)
        ));
    }

    @Override
    @GetMapping("/{reportId}")
    public ResponseEntity<SuccessResponse<AdminCollectionReportDetailRes>> getReport(
        @CurrentAdmin Long adminId,
        @PathVariable Long reportId
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminCollectionReportFacade.getReport(adminId, reportId)
        ));
    }

    @Override
    @PatchMapping("/{reportId}/resolution")
    public ResponseEntity<SuccessResponse<Void>> resolveReport(
        @CurrentAdmin Long adminId,
        @PathVariable Long reportId,
        @Valid @RequestBody AdminCollectionReportResolutionReq request
    ) {
        adminCollectionReportFacade.resolveReport(adminId, reportId, request);
        return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_UPDATE));
    }
}
