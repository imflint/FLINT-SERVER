package kr.flint.api.domain.content.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.service.ContentQueryFacade;
import kr.flint.ott.dto.GetOttResponse;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/contents")
public class ContentController {
	private final ContentQueryFacade contentQueryFacade;

	@GetMapping("/ott/{contentId}")
	public ResponseEntity<SuccessResponse<List<GetOttResponse>>> getOttList(
		//@AuthenticationPrincipal Long userId,
		@PathVariable Long contentId
	){
		List<GetOttResponse> getOttResponseList = contentQueryFacade.getOttList(1L, contentId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, getOttResponseList));
	}

	@GetMapping
	public ResponseEntity<SuccessResponse<?>> getBookmarkContent(
		@AuthenticationPrincipal Long userId
	){
		List<GetContentDetailRes> getContentDetailResList = contentQueryFacade.getContentDetailList(userId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, getContentDetailResList));
	}
}
