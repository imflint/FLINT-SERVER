package kr.flint.api.domain.search.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.search.controller.spec.SearchControllerDocs;
import kr.flint.api.domain.search.dto.response.BookmarkedCollectionSearchRes;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;
import kr.flint.api.domain.content.service.ContentQueryFacade;
import kr.flint.api.domain.search.dto.GetContentSearchRes;
import kr.flint.api.domain.search.dto.GetSearchBookmarkContentRes;
import kr.flint.api.domain.search.service.SearchQueryFacade;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class SearchController implements SearchControllerDocs {

	private final SearchQueryFacade searchQueryFacade;

	@GetMapping("/contents")
	public ResponseEntity<SuccessResponse<List<GetContentSearchRes>>> searchContent(
		@RequestParam(name = "keyword", required = false) String keyword
	){
		List<GetContentSearchRes> contentSearchResList = searchQueryFacade.searchContent(keyword);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, contentSearchResList));
	}
	@Override
	@GetMapping("/bookmarked-collections")
	public ResponseEntity<SuccessResponse<PaginationResponse<BookmarkedCollectionSearchRes>>> searchBookmarkedCollections(

		@RequestParam("keyword") String keyword,
		@RequestParam(value = "cursor", required = false) Long cursor,
		@RequestParam(value = "size", defaultValue = "20") int size
	) {
		PaginationResponse<BookmarkedCollectionSearchRes> response =
			searchQueryFacade.searchBookmarkedCollections(userId, keyword, cursor, size);

		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, response));
	}

	@Override
	@GetMapping("/bookmarked-contents")
	public ResponseEntity<SuccessResponse<PaginationResponse<BookmarkedContentSearchRes>>> searchBookmarkedContents(
		@CurrentUser Long userId,
		@RequestParam("keyword") String keyword,
		@RequestParam(value = "cursor", required = false) Long cursor,
		@RequestParam(value = "size", defaultValue = "20") int size
	) {
		PaginationResponse<BookmarkedContentSearchRes> response =
			searchQueryFacade.searchBookmarkedContents(userId, keyword, cursor, size);

		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, response));
	}
}
