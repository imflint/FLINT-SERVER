package kr.flint.api.bookmark.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.bookmark.dto.response.GetBookmarkUserRes;
import kr.flint.api.bookmark.service.BookmarkCommandFacade;
import kr.flint.api.bookmark.service.BookmarkQueryFacade;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookmarks")
public class BookmarkController {
	private final BookmarkCommandFacade bookmarkCommandFacade;
	private final BookmarkQueryFacade bookmarkQueryFacade;

	@PostMapping("/contents/{contentId}")
	public ResponseEntity<SuccessResponse<Boolean>> postContentBookmark(
		//@AuthenticationPrincipal Long userId,
		@PathVariable("contentId") Long contentId
	){
		boolean isBookmarked = bookmarkCommandFacade.toggleContent(1L, contentId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_UPDATE, isBookmarked));
	}

	@PostMapping("/collections/{collectionId}")
	public ResponseEntity<SuccessResponse<Boolean>> postCollectionBookmark(
		//@AuthenticationPrincipal Long userId,
		@PathVariable("collectionId") Long collectionId
	){
		boolean isBookmarked = bookmarkCommandFacade.toggleCollection(1L, collectionId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_UPDATE, isBookmarked));
	}

	@GetMapping("/{collectionId}")
	public ResponseEntity<SuccessResponse<GetBookmarkUserRes>> getBookmarkedUser(
		@PathVariable("collectionId") Long collectionId
	){
		GetBookmarkUserRes getBookmarkUserRes = bookmarkQueryFacade.getBookmarkedUser(collectionId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, getBookmarkUserRes));
	}

}
