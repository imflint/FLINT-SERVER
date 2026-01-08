package kr.flint.api.collection.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.collection.service.CollectionCommandFacade;
import kr.flint.collection.dto.request.CreateCollectionReq;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collections")
public class CollectionController {
	private final CollectionCommandFacade collectionCommandFacade;

	@PostMapping
	public ResponseEntity<SuccessResponse<?>> postCollection(
		//@AuthenticationPrincipal Long userId,
		@RequestBody CreateCollectionReq createCollectionReq
	){
		collectionCommandFacade.createCollection(1L, createCollectionReq);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_CREATE));
	}
}
