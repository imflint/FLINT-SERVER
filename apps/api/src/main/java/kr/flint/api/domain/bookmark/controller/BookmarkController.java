package kr.flint.api.domain.bookmark.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.bookmark.controller.spec.BookmarkControllerDocs;
import kr.flint.api.domain.bookmark.dto.response.GetBookmarkUserRes;
import kr.flint.api.domain.bookmark.service.BookmarkCommandFacade;
import kr.flint.api.domain.bookmark.service.BookmarkQueryFacade;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookmarks")
public class BookmarkController implements BookmarkControllerDocs {
	private final BookmarkCommandFacade bookmarkCommandFacade;
	private final BookmarkQueryFacade bookmarkQueryFacade;

	@Override
	@PostMapping("/contents/{contentId}")
	public ResponseEntity<SuccessResponse<Boolean>> postContentBookmark(
		@CurrentUser Long userId,
		@PathVariable("contentId") Long contentId
	){
		boolean isBookmarked = bookmarkCommandFacade.toggleContent(userId, contentId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_UPDATE, isBookmarked));
	}

	@Override
	@PostMapping("/collections/{collectionId}")
	public ResponseEntity<SuccessResponse<Boolean>> postCollectionBookmark(
		@CurrentUser Long userId,
		@PathVariable("collectionId") Long collectionId
	){
		boolean isBookmarked = bookmarkCommandFacade.toggleCollection(userId, collectionId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_UPDATE, isBookmarked));
	}

	@Override
	@GetMapping("/{collectionId}")
	public ResponseEntity<SuccessResponse<GetBookmarkUserRes>> getBookmarkedUser(
		@PathVariable("collectionId") Long collectionId
	){
		GetBookmarkUserRes getBookmarkUserRes = bookmarkQueryFacade.getBookmarkedUser(collectionId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, getBookmarkUserRes));
	}

}
