package kr.flint.admin.domain.collection.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.admin.domain.collection.controller.spec.AdminCollectionControllerDocs;
import kr.flint.admin.domain.collection.dto.request.AdminCollectionUpdateReq;
import kr.flint.admin.domain.collection.dto.request.AdminCollectionVisibility;
import kr.flint.admin.domain.collection.dto.response.AdminCollectionDetailRes;
import kr.flint.admin.domain.collection.dto.response.AdminCollectionSummaryRes;
import kr.flint.admin.domain.collection.service.AdminCollectionFacade;
import kr.flint.admin.global.security.annotation.CurrentAdmin;
import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/collections")
public class AdminCollectionController implements AdminCollectionControllerDocs {

    private final AdminCollectionFacade adminCollectionFacade;

    @Override
    @GetMapping
    public ResponseEntity<SuccessResponse<PaginationResponse<AdminCollectionSummaryRes>>> getCollections(
        @CurrentAdmin Long adminId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) AdminCollectionVisibility visibility,
        @RequestParam(required = false) CollectionModerationStatus moderationStatus,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminCollectionFacade.getCollections(adminId, keyword, visibility, moderationStatus, page, size)
        ));
    }

    @Override
    @GetMapping("/{collectionId}")
    public ResponseEntity<SuccessResponse<AdminCollectionDetailRes>> getCollection(
        @CurrentAdmin Long adminId,
        @PathVariable Long collectionId
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_FETCH,
            adminCollectionFacade.getCollection(adminId, collectionId)
        ));
    }

    @Override
    @PutMapping("/{collectionId}")
    public ResponseEntity<SuccessResponse<AdminCollectionDetailRes>> updateCollection(
        @CurrentAdmin Long adminId,
        @PathVariable Long collectionId,
        @Valid @RequestBody AdminCollectionUpdateReq request
    ) {
        return ResponseEntity.ok(SuccessResponse.of(
            SuccessCode.SUCCESS_UPDATE,
            adminCollectionFacade.updateCollection(adminId, collectionId, request)
        ));
    }
}
