package kr.flint.api.collection.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.flint.api.collection.dto.response.GetCollectionDetailListRes;
import kr.flint.api.collection.dto.response.GetCollectionDetailRes;
import kr.flint.api.collection.service.CollectionCommandFacade;
import kr.flint.api.collection.service.CollectionQueryFacade;
import kr.flint.api.collection.service.CollectionQueryService;
import kr.flint.collection.dto.request.CreateCollectionReq;
import kr.flint.collection.dto.response.GetCollectionSimpleRes;
import kr.flint.collection.service.CollectionService;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collections")
public class CollectionController {
	private final CollectionCommandFacade collectionCommandFacade;
	private final CollectionQueryFacade collectionQueryFacade;
	private final CollectionQueryService collectionQueryService;
	private final CollectionService collectionService;

	@PostMapping
	public ResponseEntity<SuccessResponse<?>> postCollection(
		//@AuthenticationPrincipal Long userId,
		@Valid @RequestBody CreateCollectionReq createCollectionReq
	){
		collectionCommandFacade.createCollection(1L, createCollectionReq);
		return ResponseEntity
			.status(SuccessCode.SUCCESS_CREATE.getHttpStatus())
			.body(SuccessResponse.of(SuccessCode.SUCCESS_CREATE));
	}

	@GetMapping
	public ResponseEntity<SuccessResponse<PaginationResponse<GetCollectionSimpleRes>>> discoverCollectionList(
		@RequestParam(required = false) Long cursor,
		@RequestParam(defaultValue = "10") int size
	) {
		PaginationResponse<GetCollectionSimpleRes> collectionList = collectionQueryFacade.getCollectionList(cursor, size);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, collectionList));
	}

	@GetMapping("/{collectionId}")
	public ResponseEntity<SuccessResponse<GetCollectionDetailRes>> getCollectionDetail(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long collectionId
	){
		GetCollectionDetailRes collectionDetail = collectionQueryFacade.getCollectionDetail(userId, collectionId);
		collectionService.saveRecentCollection(userId, collectionId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, collectionDetail));
	}

	@GetMapping("/recent")
	public ResponseEntity<SuccessResponse<List<GetCollectionDetailListRes>>> getRecentCollectionList(
		@AuthenticationPrincipal Long userId
	){
		List<GetCollectionDetailListRes> getCollectionDetailListRes = collectionQueryService.getRecentCollectionList(userId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, getCollectionDetailListRes));
	}


}
