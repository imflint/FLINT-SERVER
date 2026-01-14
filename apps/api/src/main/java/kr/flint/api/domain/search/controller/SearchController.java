package kr.flint.api.domain.search.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.content.service.ContentQueryFacade;
import kr.flint.api.domain.search.dto.GetContentSearchRes;
import kr.flint.api.domain.search.dto.GetSearchBookmarkContentRes;
import kr.flint.api.domain.search.service.SearchQueryFacade;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class SearchController {
	private final SearchQueryFacade searchQueryFacade;

	@GetMapping("/contents")
	public ResponseEntity<SuccessResponse<List<GetContentSearchRes>>> searchContent(
		@RequestParam(name = "keyword", required = false) String keyword
	){
		List<GetContentSearchRes> contentSearchResList = searchQueryFacade.searchContent(keyword);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, contentSearchResList));
	}

	@GetMapping("/contents/bookmarks")
	public ResponseEntity<SuccessResponse<List<GetSearchBookmarkContentRes>>> searchBookmarkContent(
		@CurrentUser Long userId,
		@RequestParam(name = "keyword", required = false) String keyword
	){
		List<GetSearchBookmarkContentRes> bookmarkContentList = searchQueryFacade.searchBookmarkContent(userId, keyword);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, bookmarkContentList));
	}
}
