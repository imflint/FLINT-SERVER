package kr.flint.api.domain.content.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.content.controller.spec.ContentControllerDocs;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.dto.GetContentListRes;
import kr.flint.api.domain.content.dto.GetOttListRes;
import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.api.domain.content.service.ContentQueryFacade;
import kr.flint.content.domain.MediaType;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.ott.dto.GetOttResponse;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/contents")
public class ContentController implements ContentControllerDocs {
	private final ContentQueryFacade contentQueryFacade;

	@Override
	@GetMapping("/ott/{contentId}")
	public ResponseEntity<SuccessResponse<GetOttListRes>> getOttList(
		@CurrentUser Long userId,
		@PathVariable Long contentId
	){
		List<GetOttResponse> getOttResponseList = contentQueryFacade.getOttList(userId, contentId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, new GetOttListRes(getOttResponseList)));
	}

	@Override
	@GetMapping("/bookmarks")
	public ResponseEntity<SuccessResponse<GetContentListRes>> getBookmarkContent(
		@CurrentUser Long userId
	){
		List<GetContentDetailRes> getContentDetailResList = contentQueryFacade.getContentDetailList(userId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, GetContentListRes.from(getContentDetailResList)));
	}

	@Override
	@GetMapping("/search")
	public ResponseEntity<SuccessResponse<PaginationResponse<GetContentSearchRes>>> searchContent(
		@RequestParam(required = false, name = "keyword") String keyword,
		@RequestParam(required = false, name = "genre") List<SearchGenre> genres,
		@RequestParam(required = false, name = "mediaType") MediaType mediaType,
		@RequestParam(required = false, defaultValue = "1") int cursor,
		@RequestParam(required = false, defaultValue = "20") int size
	){
		PaginationResponse<GetContentSearchRes> searchRes =
			contentQueryFacade.getContentSearchList(keyword, genres, mediaType, cursor, size);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, searchRes));

	}

	@GetMapping("/{userId}/bookmarked-contents")
	public ResponseEntity<SuccessResponse<GetContentListRes>> getUserBookmarkedContents(
		@PathVariable Long userId
	){
		List<GetContentDetailRes> getContentDetailResList = contentQueryFacade.getContentDetailList(userId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, GetContentListRes.from(getContentDetailResList)));
	}
}
