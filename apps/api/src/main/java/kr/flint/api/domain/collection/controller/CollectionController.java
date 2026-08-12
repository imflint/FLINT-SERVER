package kr.flint.api.domain.collection.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.api.domain.collection.controller.spec.CollectionControllerDocs;
import kr.flint.api.domain.collection.dto.request.CreateCollectionReq;
import kr.flint.api.domain.collection.dto.request.ReportCollectionReq;
import kr.flint.api.domain.collection.dto.request.UpdateCollectionReq;
import kr.flint.api.domain.collection.dto.response.CreateCollectionRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailListRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionListRes;
import kr.flint.api.domain.collection.service.CollectionCommandFacade;
import kr.flint.api.domain.collection.service.CollectionQueryFacade;
import kr.flint.api.domain.collection.service.CollectionQueryService;
import kr.flint.api.domain.collection.dto.response.GetCollectionSimpleRes;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.collection.service.CollectionService;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/collections")
public class CollectionController implements CollectionControllerDocs {
	private final CollectionCommandFacade collectionCommandFacade;
	private final CollectionQueryFacade collectionQueryFacade;
	private final CollectionQueryService collectionQueryService;
	private final CollectionService collectionService;

	@Override
	@PostMapping
	public ResponseEntity<SuccessResponse<CreateCollectionRes>> postCollection(
		@CurrentUser Long userId,
		@Valid @RequestBody CreateCollectionReq createCollectionReq
	){
		Long collectionId = collectionCommandFacade.createCollection(userId, createCollectionReq);
		return ResponseEntity
			.status(SuccessCode.SUCCESS_CREATE.getHttpStatus())
			.body(SuccessResponse.of(SuccessCode.SUCCESS_CREATE, new CreateCollectionRes(collectionId)));
	}

	@Override
	@PutMapping("/{collectionId}")
	public ResponseEntity<SuccessResponse<Void>> updateCollection(
		@CurrentUser Long userId,
		@PathVariable Long collectionId,
		@Valid @RequestBody UpdateCollectionReq updateCollectionReq
	){
		collectionCommandFacade.updateCollection(userId, collectionId, updateCollectionReq);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_UPDATE));
	}

	@Override
	@DeleteMapping("/{collectionId}")
	public ResponseEntity<SuccessResponse<Void>> deleteCollection(
		@CurrentUser Long userId,
		@PathVariable Long collectionId
	){
		collectionCommandFacade.deleteCollection(userId, collectionId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_DELETE));
	}

	@Override
	@PostMapping("/{collectionId}/reports")
	public ResponseEntity<SuccessResponse<Void>> reportCollection(
		@CurrentUser Long userId,
		@PathVariable Long collectionId,
		@Valid @RequestBody ReportCollectionReq reportCollectionReq
	){
		collectionCommandFacade.reportCollection(userId, collectionId, reportCollectionReq);
		return ResponseEntity
			.status(SuccessCode.SUCCESS_REPORT.getHttpStatus())
			.body(SuccessResponse.of(SuccessCode.SUCCESS_REPORT));
	}

	@Override
	@Deprecated
	@GetMapping
	public ResponseEntity<SuccessResponse<PaginationResponse<GetCollectionSimpleRes>>> discoverCollectionList(
		@RequestParam(required = false, defaultValue = "") Long cursor,
		@RequestParam(defaultValue = "10") int size
	) {
		PaginationResponse<GetCollectionSimpleRes> collectionList = collectionQueryFacade.getCollectionList(cursor, size);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, collectionList));
	}

	@Override
	@GetMapping("/{collectionId}")
	public ResponseEntity<SuccessResponse<GetCollectionDetailRes>> getCollectionDetail(
		@CurrentUser Long userId,
		@PathVariable Long collectionId
	){
		GetCollectionDetailRes collectionDetail = collectionQueryFacade.getCollectionDetail(collectionId, userId);
		collectionService.saveRecentCollection(userId, collectionId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, collectionDetail));
	}

	@Override
	@GetMapping("/recent")
	public ResponseEntity<SuccessResponse<GetCollectionListRes>> getRecentCollectionList(
		@CurrentUser Long userId
	){
		List<GetCollectionDetailListRes> getCollectionDetailListRes = collectionQueryService.getRecentCollectionList(userId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, new GetCollectionListRes(getCollectionDetailListRes)));
	}


}
