package kr.flint.api.domain.content.controller;

import java.beans.PropertyEditorSupport;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.api.domain.content.controller.spec.ContentControllerDocs;
import kr.flint.api.domain.content.dto.GetBookmarkedContentCountRes;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
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
@Validated
public class ContentController implements ContentControllerDocs {
	private final ContentQueryFacade contentQueryFacade;

	@InitBinder("genre")
	public void initGenreBinder(WebDataBinder binder) {
		binder.registerCustomEditor(SearchGenre.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String text) {
				// 기존 반복 파라미터와 쉼표 입력은 첫 장르만 적용합니다.
				String firstGenre = text.split(",", 2)[0].trim();
				setValue(firstGenre.isEmpty() ? null : SearchGenre.valueOf(firstGenre));
			}
		});
	}

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
	public ResponseEntity<SuccessResponse<PaginationResponse<GetContentDetailRes>>> getBookmarkContent(
		@CurrentUser Long userId,
		@RequestParam(required = false, name = "cursor") Long cursor,
		@RequestParam(required = false, defaultValue = "10") int size
	){
		PaginationResponse<GetContentDetailRes> response =
			contentQueryFacade.getBookmarkedContentList(userId, cursor, size);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, response));
	}

	@Override
	@GetMapping("/bookmarks/count")
	public ResponseEntity<SuccessResponse<GetBookmarkedContentCountRes>> getBookmarkedContentCount(
		@CurrentUser Long userId
	) {
		GetBookmarkedContentCountRes response = contentQueryFacade.getBookmarkedContentCount(userId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, response));
	}

	@Override
	@GetMapping("/search")
	public ResponseEntity<SuccessResponse<PaginationResponse<GetContentSearchRes>>> searchContent(
		@RequestParam(required = false, name = "keyword") String keyword,
		@RequestParam(required = false, name = "genre") SearchGenre genre,
		@RequestParam(required = false, name = "mediaType") MediaType mediaType,
		@RequestParam(required = false, name = "cursor") String cursor,
		@RequestParam(required = false, defaultValue = "20") int size
	){
		PaginationResponse<GetContentSearchRes> searchRes =
			contentQueryFacade.getContentSearchList(keyword, genre, mediaType, cursor, size);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, searchRes));

	}
}
